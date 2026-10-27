package com.worksphere.file;

import java.io.InputStream;

/**
 * Where file bytes live. The rest of the module only knows this interface, so the storage product
 * (MinIO locally, S3 or similar in the cloud) can change without touching business code, and unit
 * tests can use a fake.
 */
public interface ObjectStorage {

    /** @throws StorageUnavailableException if the storage cannot be reached or refuses the write. */
    void put(String key, byte[] content, String contentType);

    /** @throws StorageUnavailableException if the storage cannot be reached or the object is missing. */
    InputStream get(String key);

    /** Best effort: a failure is reported to the caller, who decides whether it matters. */
    void delete(String key);
}
