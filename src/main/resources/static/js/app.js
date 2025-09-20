/* ==========================================================================
   Repairo Admin - Unified JS
   Features:
   - Poller utility with visibility pause + backoff
   - Notification / toast system
   - CSRF-aware POST helper
   - Debounce utility
   - Page modules: DashboardPage, MessagesPage, CustomersPage, RepairsPage
   - Client-side pagination for customers
   ========================================================================== */

(function() {
  'use strict';

  /* ------------------ UTILITIES ------------------ */

  const qs = (sel, ctx=document) => ctx.querySelector(sel);
  const qsa = (sel, ctx=document) => Array.from(ctx.querySelectorAll(sel));

  function debounce(fn, delay=200) {
    let t;
    return (...args) => {
      clearTimeout(t);
      t = setTimeout(() => fn(...args), delay);
    };
  }

  function escapeHtml(txt='') {
    const div = document.createElement('div');
    div.textContent = txt;
    return div.innerHTML;
  }

  function getCsrf() {
    const token = qs('meta[name="_csrf"]')?.content;
    const header = qs('meta[name="_csrf_header"]')?.content;
    return token && header ? { token, header } : null;
  }

  function postForm(url, formObj={}) {
    const body = Object.entries(formObj)
      .filter(([,v]) => v !== undefined && v !== null)
      .map(([k,v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
    const csrf = getCsrf();
    const headers = { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept':'application/json,text/plain;q=0.9' };
    if (csrf) headers[csrf.header] = csrf.token;
    return fetch(url, { method:'POST', headers, body })
      .then(async r => {
        const ct = r.headers.get('Content-Type') || '';
        if (ct.includes('application/json')) {
          try { const json = await r.json(); return { ok: r.ok, json, raw: json, text: null }; } catch(e){ return { ok:r.ok, json:null, raw:null, text: await r.text() }; }
        }
        const text = await r.text();
        return { ok: r.ok, json: null, raw: text, text };
      });
  }

  // JSON POST helper (canonical going forward)
  function postJson(url, payload={}) {
    const csrf = getCsrf();
    const headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
    if (csrf) headers[csrf.header] = csrf.token;
    return fetch(url, { method:'POST', headers, body: JSON.stringify(payload) })
      .then(async r => {
        let data = null;
        try { data = await r.json(); } catch(_){ /* ignore parse errors */ }
        return { ok: r.ok, json: data };
      });
  }

  /* ------------------ NOTIFICATIONS ------------------ */
  const Toast = (() => {
    let container;
    function ensureContainer() {
      if (!container) {
        container = document.createElement('div');
        container.className = 'toast-container-repairo';
        document.body.appendChild(container);
      }
    }
    function show(message, type='info', { timeout=4000, icon=null } = {}) {
      ensureContainer();
      const el = document.createElement('div');
      el.className = `r-toast ${type}`;
      el.setAttribute('role','status');
      el.setAttribute('aria-live','polite');
      el.innerHTML = `
        <div style="display:flex; gap:.55rem; width:100%;">
          ${icon ? `<span style="font-size:.85rem;margin-top:2px;">${icon}</span>` : ''}
          <span style="flex:1;">${escapeHtml(message)}</span>
          <button class="close-btn" aria-label="Dismiss">&times;</button>
        </div>`;
      el.querySelector('.close-btn').addEventListener('click', () => dismiss(el));
      container.appendChild(el);
      if (timeout > 0) {
        setTimeout(() => dismiss(el), timeout);
      }
      return el;
    }
    function dismiss(el) {
      if (!el || !el.parentNode) return;
      el.style.opacity = '0';
      el.style.transform = 'translateY(-4px)';
      setTimeout(() => el.remove(), 300);
    }
    return { show, dismiss };
  })();

  /* ------------------ POLLER ------------------ */
  class Poller {
    constructor({ url, interval=5000, onData, onError, autoStart=true, backoff=true, maxInterval=60000, jitter=0.15 }) {
      this.url = url; // can be string or function returning string
      this.interval = interval;
      this.baseInterval = interval;
      this.currentInterval = interval;
      this.onData = onData;
      this.onError = onError;
      this.backoff = backoff;
      this.maxInterval = maxInterval;
      this.jitter = jitter;
      this.timer = null;
      this.active = false;
      this.failCount = 0;
      this.lastSuccess = 0;
      document.addEventListener('visibilitychange', () => {
        if (document.hidden) this.stop();
        else if (!this.active) this.start();
      });
      if (autoStart) this.start();
    }
    start() {
      if (this.active) return;
      this.active = true;
      this.currentInterval = this.baseInterval;
      this._tick();
    }
    stop() {
      this.active = false;
      clearTimeout(this.timer);
    }
    _computeDelay() {
      const jitterAmount = this.currentInterval * this.jitter;
      return this.currentInterval + (Math.random()*jitterAmount - jitterAmount/2);
    }
    _schedule() {
      if (!this.active) return;
      const delay = this._computeDelay();
      this.timer = setTimeout(() => this._tick(), delay);
    }
    _resolveUrl() {
      return (typeof this.url === 'function') ? this.url() : this.url;
    }
    _tick() {
      if (!this.active) return;
      const u = this._resolveUrl();
      fetch(u, { headers: { 'Accept':'application/json' } })
        .then(r => r.json())
        .then(data => {
          this.failCount = 0;
          this.currentInterval = this.baseInterval;
          this.lastSuccess = Date.now();
          this.onData?.(data);
        })
        .catch(err => {
          this.failCount++;
          if (this.backoff) {
            this.currentInterval = Math.min(this.baseInterval * Math.pow(2, this.failCount), this.maxInterval);
          }
          this.onError?.(err);
        })
        .finally(() => this._schedule());
    }
  }

  // Real-time coordination state
  const realTimeState = {
    lastActivity: Date.now(),
    ws: { connected: false, reconnects: 0, client: null }
  };

  /* ------------------ DASHBOARD PAGE ------------------ */
  const DashboardPage = (() => {
    let polling;
    let lastChecked = new Date().toISOString();
    function init() {
      const root = qs('[data-page="dashboard"]');
      if (!root) return;
      const intervalMeta = qs('meta[name="poll.dashboard.interval"]');
      const dashInterval = intervalMeta ? parseInt(intervalMeta.content,10) : 10000;
      polling = new Poller({
        url: `/admin/check-new-messages?lastChecked=${encodeURIComponent(lastChecked)}`,
        interval: dashInterval,
        onData: handleData,
        onError: () => {}
      });
    }
    function handleData(resp) {
      const data = resp && resp.data ? resp.data : resp; // unwrap ApiResponse
      if (!data) return;
      if (data.timestamp) lastChecked = data.timestamp;
      if (data.hasNewMessages && data.newMessageCount > 0) {
        notifyNewMessages(data.newMessageCount);
        updatePendingMessages(data.newMessageCount);
      }
    }
    function notifyNewMessages(count) {
      Toast.show(`${count} new message${count>1?'s':''}. Click to view.`, 'success', {
        timeout: 8000,
        icon: '<i class="fas fa-comments"></i>'
      }).addEventListener('click', () => { window.location.href='/admin/messages'; });
    }
    function updatePendingMessages(delta) {
      const el = qs('[data-stat="pending-messages"] .stat-number');
      if (!el) return;
      const current = parseInt(el.textContent) || 0;
      el.textContent = current + delta;
      el.classList.add('updated');
      setTimeout(()=> el.classList.remove('updated'), 1200);
    }
    return { init };
  })();

  /* ------------------ MESSAGES PAGE ------------------ */
  const MessagesPage = (() => {
    let state = {
      lastChecked: null,
      customers: [],
      currentCustomerId: null,
      justSent: false
    };
    let els = {};
    let messagesPoller = null;
    function init() {
      if (!qs('[data-page="messages"]')) return;
      cache();
      bind();
      state.lastChecked = new Date().toISOString();
      // initial fetch, then start Poller
      fetchOnce(true).finally(() => {
        selectFirst();
        startPoller();
      });
      // Safety: visibility resume if poller somehow inactive
      document.addEventListener('visibilitychange', () => {
        if (!document.hidden && messagesPoller && !messagesPoller.active) {
          messagesPoller.start();
        }
      });
    }
    function cache() {
      els = {
        customerList: qs('.customer-list'),
        search: qs('#searchCustomer'),
        chatHeader: qs('#chatHeader'),
        avatar: qs('#customerAvatar'),
        cName: qs('#customerName'),
        cPhone: qs('#customerPhone'),
        empty: qs('#emptyState'),
        messages: qs('#messagesContainer'),
        replyArea: qs('#replyArea'),
        replyInput: qs('#replyMessage'),
        sendBtn: qs('#sendButton')
      };
    }
    function bind() {
      if (els.replyInput) {
        els.replyInput.addEventListener('keypress', e => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
          }
        });
      }
      if (els.search) {
        els.search.addEventListener('input', debounce(filterCustomers, 180));
      }
    }
    function startPoller() {
      // After first full load we may switch to diff mode (lighter payload) based on feature flag
      const diffFeatureMeta = qs('meta[name="feature.diffPolling"]');
      let useDiff = diffFeatureMeta ? (diffFeatureMeta.content === 'true') : true;
      const intervalMeta = qs('meta[name="poll.messages.interval"]');
      const maxIntervalMeta = qs('meta[name="poll.messages.maxInterval"]');
      const baseInterval = intervalMeta ? parseInt(intervalMeta.content,10) : 4000;
      const maxInterval = maxIntervalMeta ? parseInt(maxIntervalMeta.content,10) : 30000;
      messagesPoller = new Poller({
        url: () => {
          const base = useDiff
            ? `/admin/check-new-messages?diff=true&lastChecked=${encodeURIComponent(state.lastChecked)}`
            : `/admin/check-new-messages?lastChecked=${encodeURIComponent(state.lastChecked)}`;
          return base;
        },
        interval: baseInterval,
        backoff: true,
        maxInterval: maxInterval,
        onData: (data) => {
          // Detect if we are still in full mode and can transition
            const payload = data && data.data ? data.data : data;
            const wasFull = !useDiff && diffFeatureMeta && diffFeatureMeta.content === 'true';
            handlePollData(data, false, {diffMode: useDiff});
            if (wasFull) {
              // After first successful full response, shift to diff mode
              useDiff = true;
            }
        },
        onError: () => {},
        autoStart: true
      });
    }

    function fetchOnce(silent=false) {
      return fetch(`/admin/check-new-messages?lastChecked=${encodeURIComponent(state.lastChecked)}`)
        .then(r => r.json())
        .then(resp => handlePollData(resp, silent))
        .catch(err => { if (!silent) console.error(err); });
    }

    function handlePollData(resp, silent=false, { diffMode=false } = {}) {
      const data = resp && resp.data ? resp.data : resp;
      if (!data) return;
      if (data.timestamp) state.lastChecked = data.timestamp;
      if (data.customers) {
        // Merge customers incrementally to allow message append without full re-render
        let listChanged = false;
        data.customers.forEach(incoming => {
          const idx = state.customers.findIndex(c => c.customerId === incoming.customerId);
          if (idx === -1) {
            // New conversation entirely
            state.customers.push(incoming);
            listChanged = true;
          } else {
            const existing = state.customers[idx];
            if (diffMode && incoming.messageCount !== undefined && incoming.lastMessageTime) {
              // Diff shape (no messages). If messageCount increased for current chat, trigger a focused fetch.
              if (existing.customerId === state.currentCustomerId) {
                const currentCount = (existing.messages || []).length;
                if (incoming.messageCount > currentCount) {
                  // Fetch full messages for this single customer (fallback: full refresh call)
                  fetchFullMessagesForCustomer(existing.customerId);
                }
              }
              // Update meta fields
              existing.repairStatus = incoming.repairStatus || existing.repairStatus;
              existing.lastInteraction = incoming.lastInteraction || existing.lastInteraction;
              listChanged = true;
            } else if (!diffMode) {
              // Full payload path (with messages present)
              const oldLen = (existing.messages || []).length;
              const newLen = (incoming.messages || []).length;
              if (newLen > oldLen) {
                const newMessages = incoming.messages.slice(oldLen);
                existing.messages = existing.messages ? existing.messages.concat(newMessages) : incoming.messages;
                if (existing.customerId === state.currentCustomerId) {
                  appendMessages(existing, newMessages);
                }
                listChanged = true;
              } else if (newLen < oldLen) {
                existing.messages = incoming.messages;
                if (existing.customerId === state.currentCustomerId) {
                  renderMessages(existing, { force:true });
                }
                listChanged = true;
              } else {
                existing.name = incoming.name;
                existing.phone = incoming.phone;
              }
            }
          }
        });
        if (listChanged) refreshCustomerList();
      }
      if (data.hasNewMessages && data.newMessageCount > 0 && !silent && !state.justSent) {
        Toast.show(`${data.newMessageCount} new message${data.newMessageCount>1?'s':''}`, 'success', {
          icon:'<i class="fas fa-comments"></i>', timeout:6000
        }).addEventListener('click', ()=> window.location.reload());
      }
      if (state.currentCustomerId) {
        const current = state.customers.find(c => c.customerId === state.currentCustomerId);
        if (current) {
          // If no chat-messages wrapper exists (initial or forced), render fully
            const wrap = els.messages?.querySelector('.chat-messages');
            if (!wrap) {
              renderMessages(current, { force:true });
            }
        }
      }
    }

    async function fetchFullMessagesForCustomer(customerId) {
      try {
        // Leverage existing endpoint without diff to get fresh full payload for one customer (filter client-side)
        const resp = await fetch(`/admin/check-new-messages?lastChecked=${encodeURIComponent(state.lastChecked)}`);
        const json = await resp.json();
        const data = json && json.data ? json.data : json;
        if (data && data.customers) {
          const fresh = data.customers.find(c => c.customerId === customerId);
          if (fresh) {
            const idx = state.customers.findIndex(c => c.customerId === customerId);
            if (idx >= 0) {
              state.customers[idx].messages = fresh.messages || [];
              if (state.currentCustomerId === customerId) {
                renderMessages(state.customers[idx], { force:true });
              }
            }
          }
        }
      } catch(e){ /* ignore */ }
    }

    function refreshCustomerList() {
      if (!els.customerList) return;
      els.customerList.innerHTML = '';
      if (state.customers.length === 0) {
        els.customerList.innerHTML = `
          <div class="empty-state" style="padding:2.2rem 1rem;">
            <i class="fas fa-users mb-2" style="font-size:2.4rem;"></i>
            <h5>No Conversations</h5>
            <p>Messages appear when users contact you.</p>
          </div>`;
        return;
      }
      state.customers.forEach(c => {
        const last = c.messages?.[c.messages.length-1];
        const lastTime = last ? new Date(last.timestamp).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}) : '';
        const lastText = last ? (last.text.length>35 ? last.text.slice(0,35)+'...' : last.text) : 'No messages';
        const isActive = c.customerId === state.currentCustomerId;
        const isNew = last && last.from === 'customer' &&
                      new Date(last.timestamp) > new Date(Date.now()-30000);
        const div = document.createElement('div');
        div.className = `customer-item ${isActive?'active':''}`;
        div.dataset.customerId = c.customerId;
        div.innerHTML = `
          <div class="d-flex align-items-center">
            <div class="me-3">
              <div class="avatar-circle">${escapeHtml((c.name||'U').substring(0,1).toUpperCase())}</div>
            </div>
            <div class="flex-grow-1 min-width-0">
              <div class="d-flex justify-content-between align-items-start">
                <h6 class="mb-1 fw-semibold text-truncate" style="max-width:140px;">
                  ${escapeHtml(c.name||'Customer')}
                  ${isNew?'<span class="badge-soft ms-1" style="background:var(--success);color:#fff;">NEW</span>':''}
                </h6>
                <small class="text-muted">${lastTime}</small>
              </div>
              <p class="mb-1 text-muted small text-truncate">${escapeHtml(c.phone||'')}</p>
              <small class="text-muted text-truncate d-block">${escapeHtml(lastText)}</small>
            </div>
          </div>`;
        div.addEventListener('click', () => selectCustomer(div));
        els.customerList.appendChild(div);
      });
    }

    function selectFirst() {
      if (state.customers.length > 0) {
        state.currentCustomerId = state.customers[0].customerId;
        refreshCustomerList();
        renderMessages(state.customers[0]);
        showChatInterface(state.customers[0]);
      }
    }

    function selectCustomer(el) {
      qsa('.customer-item').forEach(i=> i.classList.remove('active'));
      el.classList.add('active');
      state.currentCustomerId = el.dataset.customerId;
      const c = state.customers.find(cc => cc.customerId === state.currentCustomerId);
      if (c) {
        showChatInterface(c);
        renderMessages(c);
      }
    }

    function showChatInterface(customer) {
      if (!customer) return;
      els.chatHeader.style.display='flex';
      els.empty.style.display='none';
      els.messages.style.display='block';
      els.replyArea.style.display='block';
      els.cName.textContent = customer.name || 'Customer';
      els.cPhone.textContent = customer.phone || '';
      els.avatar.textContent = (customer.name||'U').substring(0,1).toUpperCase();
    }

    function renderMessages(customer, { force=false } = {}) {
      if (!els.messages) return;
      const container = els.messages;
      if (!customer.messages || customer.messages.length === 0) {
        container.innerHTML = `
          <div class="empty-state" style="min-height:240px;padding:2rem 1rem;">
            <i class="fas fa-comments mb-2" style="font-size:2.4rem;"></i>
            <p class="mb-0">No messages yet</p>
          </div>`;
        return;
      }
      container.innerHTML = '';
      const wrap = document.createElement('div');
      wrap.className='chat-messages';
      customer.messages.forEach(m => wrap.appendChild(buildBubble(customer, m)));
      container.appendChild(wrap);
      container.scrollTop = container.scrollHeight;
    }

    function buildBubble(customer, m) {
      const bub = document.createElement('div');
      bub.className = 'message-bubble ' + (m.from === 'customer' ? 'message-customer' : 'message-admin');
      const time = new Date(m.timestamp).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});
      bub.innerHTML = `
        <div class="message-sender">${m.from === 'admin' ? 'You' : escapeHtml(customer.name || 'Customer')}</div>
        <div class="message-text">${escapeHtml(m.text)}</div>
        <div class="message-time">${time}</div>`;
      return bub;
    }

    function appendMessages(customer, newMessages) {
      if (!els.messages || !newMessages || newMessages.length === 0) return;
      const container = els.messages;
      let wrap = container.querySelector('.chat-messages');
      if (!wrap) {
        // Fallback: full render if wrapper missing
        renderMessages(customer, { force:true });
        return;
      }
      const atBottom = isNearBottom(container);
      newMessages.forEach(m => wrap.appendChild(buildBubble(customer, m)));
      if (atBottom) {
        container.scrollTop = container.scrollHeight;
      }
    }

    function isNearBottom(scroller, threshold=40) {
      return (scroller.scrollHeight - scroller.scrollTop - scroller.clientHeight) < threshold;
    }

    function filterCustomers() {
      const term = els.search.value.toLowerCase();
      qsa('.customer-item').forEach(item => {
        const name = item.querySelector('h6')?.textContent.toLowerCase() || '';
        const phone = item.querySelector('p')?.textContent.toLowerCase() || '';
        item.style.display = (name.includes(term) || phone.includes(term)) ? 'block' : 'none';
      });
    }

    function sendMessage() {
      const val = els.replyInput.value.trim();
      if (!val || !state.currentCustomerId) {
        Toast.show('Enter a message', 'warning');
        return;
      }
      els.sendBtn.disabled = true;
      els.sendBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i>';
      postJson('/admin/send-message', { customerId: state.currentCustomerId, message: val })
      .then(res => {
        const success = !!(res.json && res.json.success);
        if (success) {
          state.justSent = true;
          els.replyInput.value = '';
          // Add to current chat quickly
            const customer = state.customers.find(c => c.customerId === state.currentCustomerId);
            if (customer) {
              customer.messages = customer.messages || [];
              customer.messages.push({
                from:'admin',
                text: val,
                timestamp: new Date().toISOString()
              });
              renderMessages(customer);
              refreshCustomerList(); // update last message snippet
            }
          setTimeout(()=> {
            state.justSent = false;
          }, 2000);
        } else {
          const errMsg = (res.json && (res.json.message || res.json.error)) || 'Failed to send message';
          Toast.show(errMsg, 'danger');
        }
      })
      .catch(()=> Toast.show('Error sending message','danger'))
      .finally(()=> {
        els.sendBtn.disabled = false;
        els.sendBtn.innerHTML = '<i class="fas fa-paper-plane"></i>';
      });
    }

    return { init };
  })();

  /* ------------------ CUSTOMERS PAGE ------------------ */
  const CustomersPage = (() => {
    let tableBody, paginationBar;
    const state = {
      rows: [],
      page: 1,
      size: 50
    };
    function init() {
      if (!qs('[data-page="customers"]')) return;
      tableBody = qs('#customersTableBody');
      if (!tableBody) return;
      collectRows();
      injectPagination();
      render();
      bindSearchDebounce();
    }
    function collectRows() {
      state.rows = qsa('tr[data-customer-row]', tableBody);
    }
    function totalPages() {
      return Math.max(1, Math.ceil(state.rows.length / state.size));
    }
    function injectPagination() {
      const container = qs('#customersPaginationContainer');
      if (!container) return;
      container.innerHTML = `
        <div class="pagination-bar" aria-label="Customers pagination">
          <div class="d-flex align-items-center gap-2">
            <span>Rows per page:</span>
            <select class="page-size-select" id="pageSizeSelect">
              <option value="25">25</option>
              <option value="50" selected>50</option>
              <option value="100">100</option>
            </select>
          </div>
          <div class="pagination-controls">
            <button id="firstPageBtn" aria-label="First Page">&laquo;</button>
            <button id="prevPageBtn" aria-label="Previous Page">&lsaquo;</button>
            <span id="pageInfo"></span>
            <button id="nextPageBtn" aria-label="Next Page">&rsaquo;</button>
            <button id="lastPageBtn" aria-label="Last Page">&raquo;</button>
          </div>
        </div>`;
      paginationBar = container;
      bindPagination();
    }
    function bindPagination() {
      qs('#pageSizeSelect').addEventListener('change', e => {
        state.size = parseInt(e.target.value,10);
        state.page = 1;
        render();
      });
      qs('#firstPageBtn').addEventListener('click', ()=> { state.page=1; render(); });
      qs('#prevPageBtn').addEventListener('click', ()=> { if(state.page>1){state.page--; render();} });
      qs('#nextPageBtn').addEventListener('click', ()=> { if(state.page<totalPages()){state.page++; render();} });
      qs('#lastPageBtn').addEventListener('click', ()=> { state.page=totalPages(); render(); });
    }
    function bindSearchDebounce() {
      const searchInput = qs('input[name="search"]');
      if (!searchInput) return;
      searchInput.addEventListener('input', debounce(() => {
        // Allow server-side search (form submit) OR client quick filter of existing results
        if (searchInput.value.trim().length === 0) {
          state.rows.forEach(r => r.style.display = '');
          collectRows();
          state.page=1;
          render();
          return;
        }
        const term = searchInput.value.toLowerCase();
        state.rows.forEach(r => {
          const nameCell = r.querySelector('[data-col="name"]')?.textContent.toLowerCase() || '';
          r.style.display = nameCell.includes(term) ? '' : 'none';
        });
        // Re-collect only visible
        state.rows = qsa('tr[data-customer-row]', tableBody).filter(r => r.style.display !== 'none');
        state.page=1;
        render();
      }, 220));
    }
    function render() {
      const start = (state.page-1)*state.size;
      const end = start + state.size;
      state.rows.forEach((r,i) => {
        r.style.display = (i>=start && i<end) ? '' : 'none';
      });
      const info = qs('#pageInfo');
      if (info) info.textContent = `Page ${state.page} / ${totalPages()}`;
      qs('#prevPageBtn').disabled = state.page === 1;
      qs('#firstPageBtn').disabled = state.page === 1;
      qs('#nextPageBtn').disabled = state.page === totalPages();
      qs('#lastPageBtn').disabled = state.page === totalPages();
    }
    return { init };
  })();

  /* ------------------ REPAIRS PAGE ------------------ */
  const RepairsPage = (() => {
    function init() {
      if (!qs('[data-page="repairs"]')) return;
      // Nothing heavy yet — status updates handled by inline events replaced by delegation optionally.
      delegateStatusUpdates();
    }
    function delegateStatusUpdates() {
      document.addEventListener('change', e => {
        if (e.target.matches('.repair-status-select')) {
          updateRepairStatus(e.target);
        }
      });
    }
    function updateRepairStatus(selectEl) {
      const customerId = selectEl.getAttribute('data-customer-id');
      const status = selectEl.value;
      selectEl.disabled = true;
      postJson('/admin/update-status', { customerId, status })
        .then(res => {
          const success = !!(res.json && res.json.success);
          if (success) {
            updateBadge(selectEl, status);
            recalcStats();
            flash(selectEl.closest('.repair-card'));
            Toast.show('Status updated','success',{timeout:2500});
          } else {
            const errMsg = (res.json && (res.json.message || res.json.error)) || 'Update failed';
            Toast.show(errMsg,'danger');
          }
        })
        .catch(()=> Toast.show('Error updating','danger'))
        .finally(()=> selectEl.disabled=false);
    }
    function updateBadge(root, status) {
      const badgeSpan = root.closest('.repair-card').querySelector('.status-badge span');
      if (!badgeSpan) return;
      badgeSpan.textContent = label(status);
      badgeSpan.className = classFor(status);
    }
    function label(s) {
      switch(s){
        case 'PENDING': return 'Pending';
        case 'IN_PROGRESS': return 'In Progress';
        case 'COMPLETED': return 'Completed';
        default: return 'Unknown';
      }
    }
    function classFor(s) {
      switch(s){
        case 'PENDING': return 'status-pending';
        case 'IN_PROGRESS': return 'status-in-progress';
        case 'COMPLETED': return 'status-completed';
        default: return 'status-pending';
      }
    }
    function recalcStats() {
      const counts = { pending:0, progress:0, completed:0 };
      qsa('.repair-card .status-badge span').forEach(el => {
        if (el.classList.contains('status-pending')) counts.pending++;
        else if (el.classList.contains('status-in-progress')) counts.progress++;
        else if (el.classList.contains('status-completed')) counts.completed++;
      });
      const p = qs('#countPending'); if (p) p.textContent = counts.pending;
      const i = qs('#countProgress'); if (i) i.textContent = counts.progress;
      const c = qs('#countCompleted'); if (c) c.textContent = counts.completed;
    }
    function flash(card) {
      if (!card) return;
      card.classList.remove('status-updated');
      void card.offsetWidth;
      card.classList.add('status-updated');
    }
    return { init };
  })();

  /* ------------------ SHARED STATUS UPDATE (Customers table) ------------------ */
  function wireCustomerStatusUpdates() {
    document.addEventListener('change', e => {
      if (e.target.matches('select[data-customer-id].customer-status-select')) {
        const selectEl = e.target;
        const customerId = selectEl.getAttribute('data-customer-id');
        const status = selectEl.value;
        selectEl.disabled = true;
        const version = selectEl.getAttribute('data-version');
        postJson('/admin/update-status', { customerId, status, version })
          .then(res => {
            const success = !!(res.json && res.json.success);
            if (success) {
              Toast.show('Status updated','success',{timeout:2000});
              const row = selectEl.closest('tr');
              if (row) {
                row.classList.remove('status-updated');
                void row.offsetWidth;
                row.classList.add('status-updated');
              }
            } else if (res.json && (res.json.message || res.json.error || '').toLowerCase().includes('modified')) {
              Toast.show('Version conflict – refresh page','warning');
            } else {
              const errMsg = (res.json && (res.json.message || res.json.error)) || 'Failed to update';
              Toast.show(errMsg,'danger');
            }
          })
          .catch(()=> Toast.show('Error updating','danger'))
          .finally(()=> selectEl.disabled=false);
      }
    });
  }

  /* ------------------ MESSAGE MODAL (Customers page) ------------------ */
  function wireMessageModal() {
    let currentId = null;
    window.showMessageModal = (btn) => {
      currentId = btn.getAttribute('data-customer-id');
      const modalEl = qs('#messageModal');
      if (!modalEl) return;
      const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
      modal.show();
    };
    window.sendMessage = () => {
      const area = qs('#messageText');
      const val = (area.value||'').trim();
      const btn = qs('#messageModal .btn-primary');
      if (!val) {
        Toast.show('Enter a message','warning');
        return;
      }
      btn.disabled = true;
      btn.innerHTML = '<i class="fas fa-spinner fa-spin me-2"></i>Sending...';
      postJson('/admin/send-message', { customerId: currentId, message: val })
        .then(res=>{
          const success = !!(res.json && res.json.success);
          if (success) {
            Toast.show('Message sent','success',{timeout:2500});
            area.value='';
            bootstrap.Modal.getInstance(qs('#messageModal')).hide();
          } else {
            const errMsg = (res.json && (res.json.message || res.json.error)) || 'Send failed';
            Toast.show(errMsg,'danger');
          }
        })
        .catch(()=> Toast.show('Error sending','danger'))
        .finally(()=>{
          btn.disabled=false;
          btn.innerHTML='<i class="fas fa-paper-plane me-2"></i>Send Message';
        });
    };
  }

  /* ------------------ BOOTSTRAP ALL ------------------ */
  document.addEventListener('DOMContentLoaded', () => {
    initWebSocket();
    DashboardPage.init();
    MessagesPage.init();
    CustomersPage.init();
    RepairsPage.init();
    wireCustomerStatusUpdates();
    wireMessageModal();
  });

  // Expose for rare debug
  window.Repairo = { Toast, Poller };

  /* ------------------ WEBSOCKET (STOMP) ------------------ */
  function initWebSocket() {
    if (typeof Stomp === 'undefined' && !window.Stomp) {
      loadScript('https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js', () => {
        loadScript('https://cdn.jsdelivr.net/npm/stompjs@2.3.3/lib/stomp.min.js', connectStompWithRetry);
      });
    } else {
      connectStompWithRetry();
    }
  }

  function loadScript(src, cb) {
    const s = document.createElement('script');
    s.src = src; s.async = true; s.onload = cb; s.onerror = () => cb && cb();
    document.head.appendChild(s);
  }

  function connectStompWithRetry() {
    if (!window.SockJS || !window.Stomp) return;
    try {
      const sock = new SockJS('/ws');
      const client = Stomp.over(sock);
      client.debug = () => {};
      // heartbeats (ms)
      client.heartbeat.incoming = 10000;
      client.heartbeat.outgoing = 10000;
      client.connect({}, () => {
        realTimeState.ws.connected = true;
        realTimeState.ws.reconnects = 0;
        realTimeState.ws.client = client;
        realTimeState.lastActivity = Date.now();
        client.subscribe('/topic/admin/new-messages', frame => { safeHandleWs(frame); });
        client.subscribe('/topic/admin/status-updates', frame => { safeHandleWs(frame); });
      }, () => {
        realTimeState.ws.connected = false;
        scheduleWsReconnect();
      });
    } catch(e) {
      realTimeState.ws.connected = false;
      scheduleWsReconnect();
    }
  }

  function safeHandleWs(frame) {
    try { handleWsEvent(JSON.parse(frame.body)); } catch(_){}
    realTimeState.lastActivity = Date.now();
  }

  function scheduleWsReconnect() {
    realTimeState.ws.reconnects++;
    const delay = Math.min(30000, Math.pow(2, realTimeState.ws.reconnects) * 1000);
    setTimeout(() => connectStompWithRetry(), delay);
  }

  function handleWsEvent(evt) {
    if (!evt || !evt.type) return;
    if (evt.type === 'NEW_MESSAGE') {
      Toast.show('New message received', 'success', { timeout:4000, icon:'<i class="fas fa-comments"></i>' });
    } else if (evt.type === 'STATUS_CHANGE') {
      // Optionally reflect status change if element present
      const sel = document.querySelector(`[data-customer-id="${evt.customerId}"]`);
      if (sel && sel.tagName === 'SELECT') {
        sel.value = evt.newStatus;
      }
    }
    realTimeState.lastActivity = Date.now();
  }

  // Watchdog to ensure polling resumes if WebSocket silent
  setInterval(() => {
    const now = Date.now();
    const idle = now - realTimeState.lastActivity;
    // If idle > 20s and WS not connected, we could trigger a manual poll (dashboard & messages already have loops)
    if (idle > 20000 && !realTimeState.ws.connected) {
      // Trigger a lightweight hidden fetch to keep things alive (dashboard check)
      fetch('/admin/check-new-messages?lastChecked=' + encodeURIComponent(new Date(Date.now()-60000).toISOString()), { headers: { 'Accept':'application/json' } })
        .then(r=>r.json()).then(()=>{ realTimeState.lastActivity = Date.now(); })
        .catch(()=>{});
    }
  }, 15000);
})();