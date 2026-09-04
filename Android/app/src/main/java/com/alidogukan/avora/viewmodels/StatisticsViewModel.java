package com.alidogukan.avora.viewmodels;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import com.alidogukan.avora.R;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.GardenZone;
import com.alidogukan.avora.models.WateringHistory;
import java.util.Collections;
import java.util.List;

/** One lifecycle-aware, complete-history source for all statistics filters. */
public class StatisticsViewModel extends AndroidViewModel {
    private final MediatorLiveData<List<WateringHistory>> history = new MediatorLiveData<>();
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final LiveData<List<GardenZone>> zones;

    public StatisticsViewModel(@NonNull Application application) {
        super(application);
        FirebaseRepository repository = new FirebaseRepository();
        zones = repository.observeGardenZones();
        history.addSource(repository.observeWateringHistory(databaseError ->
                error.setValue(AvoraLanguageManager.localizedContext(getApplication())
                        .getString(R.string.statistics_read_error))), values -> {
            error.setValue(null);
            history.setValue(values == null ? Collections.emptyList() : values);
        });
    }

    public LiveData<List<WateringHistory>> getHistory() { return history; }
    public LiveData<String> getError() { return error; }
    public LiveData<List<GardenZone>> getZones() { return zones; }
}
