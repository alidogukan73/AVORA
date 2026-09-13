package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.nas.NasAuthClient;
import com.alidogukan.avora.nas.NasSecurityRepository;
import com.alidogukan.avora.nas.NasSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Lifecycle-aware state and operations for the NAS Security Center. */
public final class NasSecurityViewModel extends AndroidViewModel {
    public enum Action {
        NONE,
        LOGIN,
        REGISTER,
        CREATE_INVITE,
        LOAD_ACCOUNTS,
        REVOKE_INVITE,
        LOAD_REQUESTS,
        REQUEST_ACCESS,
        APPROVE_ACCESS,
        KEEP_INACTIVE_ACCESS,
        REVOKE_DEVICE_ACCESS,
        DISABLE_ACCOUNT,
        RESTORE_ACCOUNT,
        DELETE_ACCOUNT,
        CHANGE_PASSWORD,
        REVOKE_SESSIONS,
        DISCONNECT
    }

    public enum EventType {
        CONNECTED,
        REGISTERED,
        INVITE_READY,
        INVITE_REVOKED,
        ACCOUNTS_READY,
        REQUESTS_READY,
        ACCESS_REQUESTED,
        ACCESS_APPROVED,
        INACTIVE_ACCESS_KEPT,
        DEVICE_ACCESS_REVOKED,
        ACCOUNT_DISABLED,
        ACCOUNT_RESTORED,
        ACCOUNT_DELETED,
        PASSWORD_CHANGED,
        SESSIONS_REVOKED,
        DISCONNECTED,
        SESSION_EXPIRED,
        ERROR
    }

    public static final class State {
        public final NasSession session;
        public final boolean busy;
        public final Action action;
        /** -1 checking, -2 unavailable, otherwise exact count. */
        public final int pendingRequestCount;
        public final List<NasAuthClient.SessionSummary> activeSessions;
        /** -1 loading, -2 unavailable, 0 ready. */
        public final int sessionListStatus;

        State(NasSession session, boolean busy, Action action,
              int pendingRequestCount,
              List<NasAuthClient.SessionSummary> activeSessions,
              int sessionListStatus) {
            this.session = session;
            this.busy = busy;
            this.action = action;
            this.pendingRequestCount = pendingRequestCount;
            this.activeSessions = activeSessions;
            this.sessionListStatus = sessionListStatus;
        }

        public boolean isAdministrator() {
            return session != null && "admin".equals(session.user.role);
        }
    }

    public static final class Event {
        public final EventType type;
        public final Action action;
        public final Object payload;
        public final int count;
        public final String code;
        private final long id;
        private boolean consumed;

        Event(long id, EventType type, Action action, Object payload,
              int count, String code) {
            this.id = id;
            this.type = type;
            this.action = action;
            this.payload = payload;
            this.count = count;
            this.code = code == null ? "" : code;
        }

        public synchronized boolean consume() {
            if (consumed) return false;
            consumed = true;
            return true;
        }

        public long id() {
            return id;
        }
    }

    private interface Work {
        Object run(NasSession session) throws Exception;
    }

    private interface AuthenticationWork {
        NasSession run() throws Exception;
    }

    private final NasSecurityRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>();
    private final MutableLiveData<Event> events = new MutableLiveData<>();
    private final AtomicLong eventIds = new AtomicLong();
    private volatile NasSession session;
    private volatile boolean busy;
    private volatile Action action = Action.NONE;
    private volatile int pendingRequestCount;
    private volatile List<NasAuthClient.SessionSummary> activeSessions =
            Collections.emptyList();
    private volatile int sessionListStatus;

    public NasSecurityViewModel(@NonNull Application application) {
        super(application);
        repository = new NasSecurityRepository(application);
    }

    public LiveData<State> state() {
        return state;
    }

    public LiveData<Event> events() {
        return events;
    }

    public void refresh() {
        session = repository.loadSession();
        busy = false;
        action = Action.NONE;
        pendingRequestCount = session != null && "admin".equals(session.user.role)
                ? -1 : 0;
        activeSessions = Collections.emptyList();
        sessionListStatus = session == null ? 0 : -1;
        publishState(false);
        if (session != null) refreshActiveSessions(session);
        if (session != null && "admin".equals(session.user.role)) {
            refreshPendingCount(session);
        }
    }

    public void login(String email, String password) {
        authenticate(Action.LOGIN, EventType.CONNECTED,
                () -> repository.login(email, password));
    }

    public void register(String inviteCode, String email,
                         String displayName, String password) {
        authenticate(Action.REGISTER, EventType.REGISTERED,
                () -> repository.register(inviteCode, email, displayName, password));
    }

    public void createInvite() {
        execute(Action.CREATE_INVITE, EventType.INVITE_READY,
                current -> repository.createInvite(current));
    }

    public void loadAccounts() {
        execute(Action.LOAD_ACCOUNTS, EventType.ACCOUNTS_READY,
                current -> repository.accounts(current));
    }

    public void revokeInvite(NasAuthClient.Invite invite) {
        execute(Action.REVOKE_INVITE, EventType.INVITE_REVOKED, current -> {
            repository.revokeInvite(current, invite);
            return null;
        });
    }

    public void loadPendingRequests() {
        execute(Action.LOAD_REQUESTS, EventType.REQUESTS_READY,
                current -> repository.pendingAccessRequests(current));
    }

    public void requestGardenAccess() {
        execute(Action.REQUEST_ACCESS, EventType.ACCESS_REQUESTED,
                current -> repository.requestGardenAccess(current));
    }

    public void approveAccessRequest(NasAuthClient.AccessRequest request) {
        execute(Action.APPROVE_ACCESS, EventType.ACCESS_APPROVED, current -> {
            repository.approveAccessRequest(current, request);
            return request == null ? "" : request.displayName;
        });
    }

    public void keepInactiveAccess(NasAuthClient.AccountSummary account) {
        execute(Action.KEEP_INACTIVE_ACCESS, EventType.INACTIVE_ACCESS_KEPT,
                current -> {
                    repository.keepInactiveAccess(current, account);
                    return account == null ? "" : account.displayName;
                });
    }

    public void revokeDeviceAccess(NasAuthClient.AccountSummary account) {
        execute(Action.REVOKE_DEVICE_ACCESS, EventType.DEVICE_ACCESS_REVOKED,
                current -> {
                    repository.revokeDeviceAccess(current, account);
                    return account == null ? "" : account.displayName;
                });
    }

    public void disableAccount(NasAuthClient.AccountSummary account) {
        execute(Action.DISABLE_ACCOUNT, EventType.ACCOUNT_DISABLED,
                current -> {
                    repository.disableAccount(current, account);
                    return account == null ? "" : account.displayName;
                });
    }

    public void restoreAccount(NasAuthClient.AccountSummary account) {
        execute(Action.RESTORE_ACCOUNT, EventType.ACCOUNT_RESTORED,
                current -> {
                    repository.restoreAccount(current, account);
                    return account == null ? "" : account.displayName;
                });
    }

    public void permanentlyDeleteAccount(NasAuthClient.AccountSummary account,
                                         String currentPassword) {
        execute(Action.DELETE_ACCOUNT, EventType.ACCOUNT_DELETED,
                current -> {
                    repository.permanentlyDeleteAccount(
                            current, account, currentPassword);
                    return account == null ? "" : account.displayName;
                });
    }

    public void changePassword(String currentPassword, String newPassword) {
        execute(Action.CHANGE_PASSWORD, EventType.PASSWORD_CHANGED,
                current -> repository.changePassword(
                        current, currentPassword, newPassword));
    }

    public void revokeOtherSessions() {
        execute(Action.REVOKE_SESSIONS, EventType.SESSIONS_REVOKED,
                repository::revokeOtherSessions);
    }

    public void disconnect() {
        if (busy) return;
        NasSession current = session;
        busy = true;
        action = Action.DISCONNECT;
        publishState(false);
        executor.execute(() -> {
            repository.disconnect(current);
            session = null;
            pendingRequestCount = 0;
            activeSessions = Collections.emptyList();
            sessionListStatus = 0;
            busy = false;
            action = Action.NONE;
            publishState(true);
            postEvent(EventType.DISCONNECTED, Action.DISCONNECT, null, 0, "");
        });
    }

    private void authenticate(Action requestedAction, EventType successType,
                              AuthenticationWork work) {
        if (busy) return;
        busy = true;
        action = requestedAction;
        publishState(false);
        executor.execute(() -> {
            try {
                NasSession authenticated = work.run();
                session = authenticated;
                busy = false;
                action = Action.NONE;
                pendingRequestCount = "admin".equals(authenticated.user.role) ? -1 : 0;
                activeSessions = Collections.emptyList();
                sessionListStatus = -1;
                publishState(true);
                postEvent(successType, requestedAction, authenticated, 0, "");
                refreshActiveSessions(authenticated);
                if ("admin".equals(authenticated.user.role)) {
                    refreshPendingCount(authenticated);
                }
            } catch (Exception error) {
                busy = false;
                action = Action.NONE;
                publishState(true);
                postEvent(EventType.ERROR, requestedAction, null, 0, message(error));
            }
        });
    }

    private void execute(Action requestedAction, EventType successType, Work work) {
        if (busy) return;
        NasSession current = session;
        if (current == null) {
            postEvent(EventType.SESSION_EXPIRED, requestedAction,
                    null, 0, "NAS_SESSION_EXPIRED");
            return;
        }
        busy = true;
        action = requestedAction;
        publishState(false);
        executor.execute(() -> {
            try {
                Object result = work.run(current);
                if (session != current) return;
                busy = false;
                action = Action.NONE;
                if (successType == EventType.ACCESS_APPROVED) {
                    pendingRequestCount = Math.max(0, pendingRequestCount - 1);
                }
                boolean reloadSessions = successType == EventType.SESSIONS_REVOKED;
                if (reloadSessions) {
                    activeSessions = Collections.emptyList();
                    sessionListStatus = -1;
                }
                publishState(true);
                int count = result instanceof Integer ? (Integer) result : 0;
                postEvent(successType, requestedAction, result, count, "");
                if (reloadSessions) refreshActiveSessions(current);
            } catch (Exception error) {
                fail(current, requestedAction, error);
            }
        });
    }

    private void refreshActiveSessions(NasSession expected) {
        executor.execute(() -> {
            try {
                List<NasAuthClient.SessionSummary> sessions =
                        repository.sessions(expected);
                if (session != expected) return;
                activeSessions = Collections.unmodifiableList(
                        new ArrayList<>(sessions));
                sessionListStatus = 0;
                publishState(true);
            } catch (Exception error) {
                if (session != expected) return;
                if ("NAS_SESSION_EXPIRED".equals(message(error))) {
                    repository.expireLocalSession();
                    session = null;
                    pendingRequestCount = 0;
                    activeSessions = Collections.emptyList();
                    sessionListStatus = 0;
                    publishState(true);
                    postEvent(EventType.SESSION_EXPIRED, Action.NONE,
                            null, 0, "NAS_SESSION_EXPIRED");
                } else {
                    activeSessions = Collections.emptyList();
                    sessionListStatus = -2;
                    publishState(true);
                }
            }
        });
    }

    private void refreshPendingCount(NasSession expected) {
        executor.execute(() -> {
            try {
                List<NasAuthClient.AccessRequest> requests =
                        repository.pendingAccessRequests(expected);
                if (session != expected) return;
                pendingRequestCount = requests.size();
                publishState(true);
            } catch (Exception error) {
                if (session != expected) return;
                if ("NAS_SESSION_EXPIRED".equals(message(error))) {
                    repository.expireLocalSession();
                    session = null;
                    pendingRequestCount = 0;
                    activeSessions = Collections.emptyList();
                    sessionListStatus = 0;
                    publishState(true);
                    postEvent(EventType.SESSION_EXPIRED, Action.LOAD_REQUESTS,
                            null, 0, "NAS_SESSION_EXPIRED");
                } else {
                    pendingRequestCount = -2;
                    publishState(true);
                }
            }
        });
    }

    private void fail(NasSession expected, Action failedAction, Exception error) {
        if (session != expected) return;
        String code = message(error);
        busy = false;
        action = Action.NONE;
        if ("NAS_SESSION_EXPIRED".equals(code)) {
            repository.expireLocalSession();
            session = null;
            pendingRequestCount = 0;
            activeSessions = Collections.emptyList();
            sessionListStatus = 0;
            publishState(true);
            postEvent(EventType.SESSION_EXPIRED, failedAction, null, 0, code);
        } else {
            publishState(true);
            postEvent(EventType.ERROR, failedAction, null, 0, code);
        }
    }

    private void publishState(boolean fromWorker) {
        State value = new State(
                session, busy, action, pendingRequestCount,
                Collections.unmodifiableList(new ArrayList<>(activeSessions)),
                sessionListStatus);
        if (fromWorker) state.postValue(value);
        else state.setValue(value);
    }

    private void postEvent(EventType type, Action eventAction, Object payload,
                           int count, String code) {
        events.postValue(new Event(eventIds.incrementAndGet(), type, eventAction,
                payload, count, code));
    }

    private static String message(Exception error) {
        if (error == null || error.getMessage() == null) return "";
        return error.getMessage();
    }

    @SuppressWarnings("unchecked")
    public static List<NasAuthClient.AccessRequest> requestsFrom(Event event) {
        if (event == null || !(event.payload instanceof List<?>)) {
            return Collections.emptyList();
        }
        return (List<NasAuthClient.AccessRequest>) event.payload;
    }

    @SuppressWarnings("unchecked")
    public static List<NasAuthClient.AccountSummary> accountsFrom(Event event) {
        if (event == null || !(event.payload instanceof List<?>)) {
            return Collections.emptyList();
        }
        return (List<NasAuthClient.AccountSummary>) event.payload;
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
        super.onCleared();
    }
}
