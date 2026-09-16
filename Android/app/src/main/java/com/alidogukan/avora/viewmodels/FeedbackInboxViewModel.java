package com.alidogukan.avora.viewmodels;

import androidx.lifecycle.ViewModel;

import com.alidogukan.avora.feedback.FeedbackInboxRepository;
import com.alidogukan.avora.feedback.FeedbackInboxRepository.Message;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.CommandResult;
import com.google.android.gms.tasks.Task;

import java.util.List;

/** MVVM boundary for the owner-only feedback inbox. */
public final class FeedbackInboxViewModel extends ViewModel {
    private final FeedbackInboxRepository repository = new FeedbackInboxRepository();

    public Task<Boolean> isCurrentUserOwner() { return repository.isCurrentUserOwner(); }
    public Task<List<Message>> load() { return repository.load(); }
    public Task<CommandResult> markRead(Message message) {
        return repository.updateStatus(message, "read");
    }
    public Task<CommandResult> resolve(Message message) {
        return repository.updateStatus(message, "resolved");
    }
    public Task<CommandResult> reopen(Message message) {
        return repository.updateStatus(message, "new");
    }
    public Task<CommandResult> delete(Message message) { return repository.delete(message); }
}
