package io.casehub.qhorus.runtime.spring;

import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import org.springframework.transaction.support.TransactionSynchronizationManager;

public class SpringTransactionSynchronizationRegistry implements TransactionSynchronizationRegistry {

    @Override
    public Object getTransactionKey() {
        return TransactionSynchronizationManager.getCurrentTransactionName();
    }

    @Override
    public int getTransactionStatus() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                ? Status.STATUS_ACTIVE : Status.STATUS_NO_TRANSACTION;
    }

    @Override
    public boolean getRollbackOnly() {
        return TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    @Override
    public void setRollbackOnly() {
        throw new UnsupportedOperationException("Use Spring PlatformTransactionManager to mark rollback");
    }

    @Override
    public void registerInterposedSynchronization(Synchronization sync) {
        TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void beforeCompletion() {
                        try { sync.beforeCompletion(); } catch (Exception e) { throw new RuntimeException(e); }
                    }
                    @Override
                    public void afterCompletion(int status) {
                        try { sync.afterCompletion(status == STATUS_COMMITTED ? Status.STATUS_COMMITTED : Status.STATUS_ROLLEDBACK); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    }
                });
    }

    @Override
    public Object getResource(Object key) {
        return TransactionSynchronizationManager.getResource(key);
    }

    @Override
    public void putResource(Object key, Object value) {
        TransactionSynchronizationManager.bindResource(key, value);
    }

    private static final int STATUS_COMMITTED = org.springframework.transaction.support.TransactionSynchronization.STATUS_COMMITTED;
}
