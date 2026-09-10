package com.alidogukan.avora.models;

/** Immutable result of a compressed daily seedling photo upload. */
public final class SeedlingPhotoUpload {
    private final GardenPhoto localPhoto;
    private final String storagePath;

    public SeedlingPhotoUpload(GardenPhoto localPhoto, String storagePath) {
        this.localPhoto = localPhoto;
        this.storagePath = storagePath == null ? "" : storagePath.trim();
    }

    public GardenPhoto getLocalPhoto() { return localPhoto; }
    public String getPhotoId() {
        return localPhoto == null || localPhoto.getId() == null
                ? "" : localPhoto.getId().trim();
    }
    public String getStoragePath() { return storagePath; }
}
