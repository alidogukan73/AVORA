package com.alidogukan.avora.viewmodels;

import androidx.lifecycle.ViewModel;

import com.alidogukan.avora.superadmin.SuperadminDataRepository;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.AuditItem;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.CommandResult;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.PageCursor;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.RecordPage;
import com.alidogukan.avora.superadmin.SuperadminDataRepository.RecordItem;
import com.google.android.gms.tasks.Task;

import java.util.List;

/** Keeps the protected data console behind the application's MVVM boundary. */
public final class SuperadminDataViewModel extends ViewModel {
    private final SuperadminDataRepository repository = new SuperadminDataRepository();

    public Task<Boolean> isCurrentUserOwner() {
        return repository.isCurrentUserOwner();
    }

    public Task<RecordPage> loadRecordPage(String category, PageCursor before) {
        return repository.loadRecordPage(category, before);
    }

    public Task<String> loadRecordJson(String category, String recordId) {
        return repository.loadRecordJson(category, recordId);
    }

    public Task<CommandResult> previewDelete(String category, String recordId) {
        return repository.previewDelete(category, recordId);
    }

    public Task<CommandResult> delete(
            String category, String recordId, String previewToken
    ) {
        return repository.delete(category, recordId, previewToken);
    }

    public Task<CommandResult> update(
            String category, String recordId, String replacementJson,
            String expectedRecordJson
    ) {
        return repository.update(
                category, recordId, replacementJson, expectedRecordJson);
    }

    public Task<CommandResult> restore(String backupId) {
        return repository.restore(backupId);
    }

    public Task<List<AuditItem>> loadAudit() {
        return repository.loadAudit();
    }
}
