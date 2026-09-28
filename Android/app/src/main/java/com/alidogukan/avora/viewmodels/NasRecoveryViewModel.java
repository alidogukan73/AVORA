package com.alidogukan.avora.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.alidogukan.avora.nas.NasSecurityRepository;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Keeps in-flight recovery requests across rotation; never persists passwords or codes. */
public class NasRecoveryViewModel extends AndroidViewModel {
    public static final class State {
        public final boolean busy, sent, complete;
        public final String error;
        State(boolean busy, boolean sent, boolean complete, String error) {
            this.busy = busy; this.sent = sent; this.complete = complete; this.error = error;
        }
    }
    private final NasSecurityRepository repository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, false, false, ""));
    private volatile boolean busy;
    private volatile boolean sent;

    public NasRecoveryViewModel(@NonNull Application application) {
        super(application);
        repository = new NasSecurityRepository(application);
    }
    public LiveData<State> state() { return state; }
    public void request(String email) { run(email, null, null); }
    public void confirm(String email, String code, String password) { run(email, code, password); }
    private void run(String email, String code, String password) {
        if (busy) return;
        busy = true;
        state.setValue(new State(true, sent, false, ""));
        executor.execute(() -> {
            String error = "";
            boolean complete = false;
            try {
                if (code == null) { repository.requestPasswordReset(email); sent = true; }
                else { repository.resetPassword(email, code, password); complete = true; }
            } catch (Exception failure) {
                error = failure.getMessage() == null ? "NAS_UNAVAILABLE" : failure.getMessage();
            }
            busy = false;
            state.postValue(new State(false, sent, complete, error));
        });
    }
    @Override protected void onCleared() { executor.shutdown(); }
}
