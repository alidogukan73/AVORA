package com.alidogukan.avora.viewmodels;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.alidogukan.avora.R;
import com.alidogukan.avora.models.GardenSeason;
import com.alidogukan.avora.firebase.FirebaseRepository;
import com.alidogukan.avora.language.AvoraLanguageManager;
import com.alidogukan.avora.models.WateringHistory;
import com.alidogukan.avora.season.SeasonRepository;
import com.alidogukan.avora.models.GardenZone;

import java.util.Collections;
import java.util.List;

/** Lifecycle-aware view of the complete recorded watering history. */
public class WateringHistoryViewModel extends AndroidViewModel {
    private final MediatorLiveData<List<WateringHistory>> history =
            new MediatorLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(true);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final LiveData<List<GardenZone>> zones;
    private final LiveData<List<GardenSeason>> seasons;
    private final FirebaseRepository repository;
    private LiveData<List<WateringHistory>> source;

    public WateringHistoryViewModel(@NonNull Application application) {
        super(application);
        repository = new FirebaseRepository();
        zones = repository.observeGardenZones();
        seasons = new SeasonRepository().observeAllSeasons();
        loadHistory();
    }

    public void retry() { loadHistory(); }

    private void loadHistory() {
        if (source != null) history.removeSource(source);
        loading.setValue(true);
        error.setValue(null);
        source = repository.observeWateringHistory(databaseError -> {
                    error.setValue(AvoraLanguageManager.localizedContext(
                            getApplication()).getString(
                            R.string.watering_history_read_error));
                    loading.setValue(false);
                });
        history.addSource(source, values -> {
            history.setValue(values == null ? Collections.emptyList() : values);
            error.setValue(null);
            loading.setValue(false);
        });
    }

    public LiveData<List<WateringHistory>> getHistory() { return history; }
    public LiveData<Boolean> getLoading() { return loading; }
    public LiveData<String> getError() { return error; }
    public LiveData<List<GardenZone>> getZones() { return zones; }
    public LiveData<List<GardenSeason>> getSeasons() { return seasons; }
}
