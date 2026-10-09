package com.alidogukan.avora.firebase;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Atomic multipath write, guarded by server-validated fertilizer preconditions.
 * Sensor, command, photo and notification updates never invalidate this write. */
final class ScopedFertilizerWrite {
    static final List<String> READ_PATHS = List.of("fertilizer_products", "fertilizer_history",
            "fertilizer_plans", "zones", "garden_journal/seasons");
    private static final int MAX_ATTEMPTS = 5;

    static Task<Void> run(DatabaseReference device, Consumer<FertilizerData> mutation) {
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        attempt(device, mutation, completion, 0, java.util.UUID.randomUUID().toString());
        return completion.getTask();
    }

    private static void attempt(DatabaseReference device, Consumer<FertilizerData> mutation,
                                TaskCompletionSource<Void> completion, int attempt, String operationId) {
        device.child("fertilizer_write_guard/revision").get().addOnSuccessListener(version -> {
            Object raw = version.getValue();
            long revision = raw instanceof Number ? ((Number) raw).longValue() : 0L;
            List<Task<DataSnapshot>> reads = new ArrayList<>();
            for (String path : READ_PATHS) reads.add(device.child(path).get());
            Tasks.whenAllSuccess(reads).addOnSuccessListener(ignored -> {
                Map<String, Object> values = new LinkedHashMap<>();
                for (int i = 0; i < reads.size(); i++) {
                    FertilizerData.put(values, READ_PATHS.get(i), reads.get(i).getResult().getValue());
                }
                final Map<String, Object> updates;
                try {
                    FertilizerData tree = new FertilizerData(values);
                    mutation.accept(tree);
                    updates = tree.updates(revision, operationId);
                } catch (RuntimeException error) {
                    completion.setException(error);
                    return;
                }
                device.updateChildren(updates, (error, ref) -> {
                    if (error == null) completion.setResult(null);
                    else if (error.getCode() == DatabaseError.PERMISSION_DENIED && attempt + 1 < MAX_ATTEMPTS) {
                        // A changed precondition rejects the entire update. Read
                        // fresh inputs and keep the same application IDs on retry.
                        attempt(device, mutation, completion, attempt + 1, operationId);
                    } else completion.setException(new DatabaseWriteException(error));
                });
            }).addOnFailureListener(completion::setException);
        }).addOnFailureListener(completion::setException);
    }
}
