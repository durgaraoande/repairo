package com.repairo.config;

import com.repairo.model.Customer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-time backfill for existing Customer documents created before optimistic locking (@Version) was introduced.
 * Documents without a 'version' field cause Spring Data Mongo to treat them as NEW when saving (since version is null),
 * resulting in a duplicate key error on the _id. We set an initial version=0 for any document missing the field.
 *
 * Safe to run on each startup (idempotent) as it only touches docs where 'version' does not exist.
 */
@Component
@Order(1)
public class CustomerVersionBackfillRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CustomerVersionBackfillRunner.class);
    private final MongoTemplate mongoTemplate;
    private final AtomicBoolean executed = new AtomicBoolean(false);

    public CustomerVersionBackfillRunner(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!executed.compareAndSet(false, true)) {
            return; // ensure single execution
        }
        try {
            Query missingVersion = new Query(Criteria.where("version").exists(false));
            long count = mongoTemplate.count(missingVersion, Customer.class);
            if (count == 0) {
                log.debug("Version backfill: no customer documents missing version.");
                return;
            }
            Update setVersion = new Update().set("version", 0L);
            var result = mongoTemplate.updateMulti(missingVersion, setVersion, Customer.class);
            log.info("Version backfill: initialized version=0 on {} customer document(s)", result.getModifiedCount());
        } catch (Exception e) {
            log.error("Version backfill failed: {}", e.getMessage(), e);
        }
    }
}
