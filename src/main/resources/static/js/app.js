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
      .map(([k,v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
    const csrf = getCsrf();
    const headers = { 'Content-Type': 'application/x-www-form-urlencoded' };
    if (csrf) headers[csrf.header] = csrf.token;
    return fetch(url, { method:'POST', headers, body });
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
    constructor({ url, interval=5000, onData, onError, autoStart=true, backoff=true, maxInterval=60000 }) {
      this.url = url;
      this.interval = interval;
      this.currentInterval = interval;
      this.onData = onData;
      this.onError = onError;
      this.backoff = backoff;
      this.maxInterval = maxInterval;
      this.timer = null;
      this.active = false;
      this.failCount = 0;

      document.addEventListener('visibilitychange', () => {
        if (document.hidden) this.stop();
        else if (this.active) this.start();
      });

      if (autoStart) this.start();
    }
    start() {
      if (this.active) return;
      this.active = true;
      this.currentInterval = this.interval;
      this._tick();
    }
    stop() {
      this.active = false;
      clearTimeout(this.timer);
    }
    _schedule() {
      this.timer = setTimeout(() => this._tick(), this.currentInterval);
    }
    _tick() {
      if (!this.active) return;
      fetch(this.url, { headers: { 'Accept':'application/json' } })
        .then(r => r.json())
        .then(data => {
          this.failCount = 0;
            this.currentInterval = this.interval; // reset
          this.onData?.(data);
          this._schedule();
        })
        .catch(err => {
          this.failCount++;
          if (this.backoff) {
            this.currentInterval = Math.min(this.interval * Math.pow(2, this.failCount), this.maxInterval);
          }
          this.onError?.(err);
          this._schedule();
        });
    }
  }

  /* ------------------ DASHBOARD PAGE ------------------ */
  const DashboardPage = (() => {
    let polling;
    let lastChecked = new Date().toISOString();
    function init() {
      const root = qs('[data-page="dashboard"]');
      if (!root) return;
      polling = new Poller({
        url: `/admin/check-new-messages?lastChecked=${encodeURIComponent(lastChecked)}`,
        interval: 10000,
        onData: handleData,
        onError: () => {}
      });
    }
    function handleData(data) {
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
    let polling;
    function init() {
      if (!qs('[data-page="messages"]')) return;
      cache();
      bind();
      state.lastChecked = new Date().toISOString();
      // initial silent poll then start
      fetchUpdates(true).finally(() => {
        selectFirst();
        startPolling();
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
    function startPolling() {
      polling = new Poller({
        url: () => `/admin/check-new-messages?lastChecked=${encodeURIComponent(state.lastChecked)}`,
        // allow passing function; adapt Poller:
      });
    }
    // adapt Poller to allow function url
    // (Monkey patch: instantiate new Poller each cycle)
    function fetchUpdates(silent=false) {
      return fetch(`/admin/check-new-messages?lastChecked=${encodeURIComponent(state.lastChecked)}`)
        .then(r=>r.json())
        .then(data => {
          if (data.timestamp) state.lastChecked = data.timestamp;
          if (data.customers) {
            state.customers = data.customers;
            refreshCustomerList();
          }
          if (data.hasNewMessages && data.newMessageCount > 0 && !silent && !state.justSent) {
            Toast.show(`${data.newMessageCount} new message${data.newMessageCount>1?'s':''}`, 'success', {
              icon:'<i class="fas fa-comments"></i>',
              timeout:6000
            }).addEventListener('click', ()=> window.location.reload());
          }
          if (state.currentCustomerId) {
            const current = state.customers.find(c => c.customerId === state.currentCustomerId);
            if (current) renderMessages(current);
          }
        })
        .catch(err => {
          if (!silent) console.error(err);
        });
    }
    // Re-implement polling with manual loop to allow dynamic URL
    function startPolling() {
      function loop() {
        fetchUpdates(true).finally(() => {
          setTimeout(() => {
            if (!document.hidden) loop();
          }, 3000);
        });
      }
      loop();
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

    function renderMessages(customer) {
      if (!els.messages) return;
      const container = els.messages;
      container.innerHTML = '';
      if (!customer.messages || customer.messages.length === 0) {
        container.innerHTML = `
          <div class="empty-state" style="min-height:240px;padding:2rem 1rem;">
            <i class="fas fa-comments mb-2" style="font-size:2.4rem;"></i>
            <p class="mb-0">No messages yet</p>
          </div>`;
        return;
      }
      const wrap = document.createElement('div');
      wrap.className='chat-messages';
      customer.messages.forEach(m => {
        const bub = document.createElement('div');
        bub.className = 'message-bubble ' + (m.from === 'customer' ? 'message-customer' : 'message-admin');
        const time = new Date(m.timestamp).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'});
        bub.innerHTML = `
          <div class="message-sender">${m.from === 'admin' ? 'You' : escapeHtml(customer.name || 'Customer')}</div>
          <div class="message-text">${escapeHtml(m.text)}</div>
          <div class="message-time">${time}</div>`;
        wrap.appendChild(bub);
      });
      container.appendChild(wrap);
      container.scrollTop = container.scrollHeight;
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
      postForm('/admin/send-message', {
        customerId: state.currentCustomerId,
        message: val
      })
      .then(r => r.text())
      .then(t => {
        if (t === 'success') {
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
          Toast.show('Failed to send message', 'danger');
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
      postForm('/admin/update-status', { customerId, status })
        .then(r => r.text())
        .then(t => {
          if (t === 'success') {
            updateBadge(selectEl, status);
            recalcStats();
            flash(selectEl.closest('.repair-card'));
            Toast.show('Status updated','success',{timeout:2500});
          } else {
            Toast.show('Update failed','danger');
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
        postForm('/admin/update-status', { customerId, status })
          .then(r => r.text())
          .then(t => {
            if (t === 'success') {
              Toast.show('Status updated','success',{timeout:2000});
              const row = selectEl.closest('tr');
              if (row) {
                row.classList.remove('status-updated');
                void row.offsetWidth;
                row.classList.add('status-updated');
              }
            } else {
              Toast.show('Failed to update','danger');
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
      postForm('/admin/send-message', { customerId: currentId, message: val })
        .then(r=>r.text())
        .then(t=>{
          if (t==='success') {
            Toast.show('Message sent','success',{timeout:2500});
            area.value='';
            bootstrap.Modal.getInstance(qs('#messageModal')).hide();
          } else {
            Toast.show('Send failed','danger');
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
    DashboardPage.init();
    MessagesPage.init();
    CustomersPage.init();
    RepairsPage.init();
    wireCustomerStatusUpdates();
    wireMessageModal();
  });

  // Expose for rare debug
  window.Repairo = { Toast, Poller };
})();