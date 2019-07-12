package com.sequenceiq.cloudbreak.converter.v4.stacks.cli;

import static com.sequenceiq.common.api.filesystem.FileSystemType.ADLS;
import static com.sequenceiq.common.api.filesystem.FileSystemType.ADLS_GEN_2;
import static com.sequenceiq.common.api.filesystem.FileSystemType.GCS;
import static com.sequenceiq.common.api.filesystem.FileSystemType.S3;
import static com.sequenceiq.common.api.filesystem.FileSystemType.WASB;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.convert.ConversionService;

import com.sequenceiq.common.api.cloudstorage.AdlsCloudStorageV1Parameters;
import com.sequenceiq.common.api.cloudstorage.AdlsGen2CloudStorageV1Parameters;
import com.sequenceiq.common.api.cloudstorage.GcsCloudStorageV1Parameters;
import com.sequenceiq.common.api.cloudstorage.S3CloudStorageV1Parameters;
import com.sequenceiq.common.api.cloudstorage.WasbCloudStorageV1Parameters;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.storage.CloudStorageV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.stacks.request.cluster.storage.location.StorageLocationV4Request;
import com.sequenceiq.cloudbreak.domain.FileSystem;
import com.sequenceiq.cloudbreak.domain.StorageLocation;
import com.sequenceiq.cloudbreak.domain.StorageLocations;
import com.sequenceiq.cloudbreak.common.json.Json;
import com.sequenceiq.common.api.filesystem.AdlsFileSystem;
import com.sequenceiq.common.api.filesystem.AdlsGen2FileSystem;
import com.sequenceiq.common.api.filesystem.GcsFileSystem;
import com.sequenceiq.common.api.filesystem.S3FileSystem;
import com.sequenceiq.common.api.filesystem.WasbFileSystem;

public class FileSystemToCloudStorageRequestConverterTest {

    @InjectMocks
    private FileSystemToCloudStorageV4RequestConverter underTest;

    @Mock
    private ConversionService conversionService;

    @Mock
    private FileSystem fileSystem;

    @Mock
    private Json configurations;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(fileSystem.getConfigurations()).thenReturn(configurations);
    }

    @Test
    public void testConvertWhenLocationIsNullThenEmptySetShouldBeSet() {
        // wasb just for testing reason
        when(fileSystem.getType()).thenReturn(WASB);
        when(fileSystem.getLocations()).thenReturn(null);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertNotNull(result.getLocations());
        assertTrue(result.getLocations().isEmpty());
    }

    @Test
    public void testConvertWhenLocationNotNullButItsValueNullThenEmptySetShouldBeSet() {
        // wasb just for testing reason
        when(fileSystem.getType()).thenReturn(WASB);
        Json locations = mock(Json.class);
        when(locations.getValue()).thenReturn(null);
        when(fileSystem.getLocations()).thenReturn(locations);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertNotNull(result.getLocations());
        assertTrue(result.getLocations().isEmpty());
    }

    @Test
    public void testConvertWhenLocationHasValidValueButStorageLocationsIsNullThenLocationsShouldBeEmpty() throws IOException {
        // wasb just for testing reason
        when(fileSystem.getType()).thenReturn(WASB);
        Json locations = mock(Json.class);
        when(locations.getValue()).thenReturn("some value");
        when(locations.get(StorageLocations.class)).thenReturn(null);
        when(fileSystem.getLocations()).thenReturn(locations);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertNotNull(result.getLocations());
        assertTrue(result.getLocations().isEmpty());
    }

    @Test
    public void testConvertWhelLocationsAreExistsThenTheseLocationsShouldBeStored() throws IOException {
        // wasb just for testing reason
        when(fileSystem.getType()).thenReturn(WASB);
        StorageLocations storageLocations = mock(StorageLocations.class);
        StorageLocation location = new StorageLocation();
        when(storageLocations.getLocations()).thenReturn(Collections.singleton(location));
        Json locations = mock(Json.class);
        when(locations.getValue()).thenReturn("some value");
        when(locations.get(StorageLocations.class)).thenReturn(storageLocations);
        when(fileSystem.getLocations()).thenReturn(locations);
        when(conversionService.convert(location, StorageLocationV4Request.class)).thenReturn(new StorageLocationV4Request());

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertNotNull(result.getLocations());
        assertEquals(1L, result.getLocations().size());
        verify(conversionService, times(1)).convert(any(StorageLocation.class), eq(StorageLocationV4Request.class));
    }

    @Test
    public void testConvertWhenTypeIsAdlsThenExpectedAdlsFileSystemShouldBeSet() throws IOException {
        when(fileSystem.getType()).thenReturn(ADLS);
        AdlsFileSystem adls = mock(AdlsFileSystem.class);
        when(configurations.get(AdlsFileSystem.class)).thenReturn(adls);
        AdlsCloudStorageV1Parameters expected = mock(AdlsCloudStorageV1Parameters.class);
        when(conversionService.convert(adls, AdlsCloudStorageV1Parameters.class)).thenReturn(expected);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertEquals(expected, result.getAdls());
        verify(conversionService, times(1)).convert(any(AdlsFileSystem.class), eq(AdlsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(GcsFileSystem.class), eq(GcsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(S3FileSystem.class), eq(S3CloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(WasbFileSystem.class), eq(WasbCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsGen2FileSystem.class), eq(AdlsGen2CloudStorageV1Parameters.class));
    }

    @Test
    public void testConvertWhenTypeIsGcsThenExpectedGcsFileSystemShouldBeSet() throws IOException {
        when(fileSystem.getType()).thenReturn(GCS);
        GcsFileSystem gcs = mock(GcsFileSystem.class);
        when(configurations.get(GcsFileSystem.class)).thenReturn(gcs);
        GcsCloudStorageV1Parameters expected = mock(GcsCloudStorageV1Parameters.class);
        when(conversionService.convert(gcs, GcsCloudStorageV1Parameters.class)).thenReturn(expected);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertEquals(expected, result.getGcs());
        verify(conversionService, times(1)).convert(any(GcsFileSystem.class), eq(GcsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsFileSystem.class), eq(AdlsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(S3FileSystem.class), eq(S3CloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(WasbFileSystem.class), eq(WasbCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsGen2FileSystem.class), eq(AdlsGen2CloudStorageV1Parameters.class));
    }

    @Test
    public void testConvertWhenTypeIsS3ThenExpectedS3FileSystemShouldBeSet() throws IOException {
        when(fileSystem.getType()).thenReturn(S3);
        S3FileSystem s3 = mock(S3FileSystem.class);
        when(configurations.get(S3FileSystem.class)).thenReturn(s3);
        S3CloudStorageV1Parameters expected = mock(S3CloudStorageV1Parameters.class);
        when(conversionService.convert(s3, S3CloudStorageV1Parameters.class)).thenReturn(expected);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertEquals(expected, result.getS3());
        verify(conversionService, times(1)).convert(any(S3FileSystem.class), eq(S3CloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(GcsFileSystem.class), eq(GcsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsFileSystem.class), eq(AdlsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(WasbFileSystem.class), eq(WasbCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsGen2FileSystem.class), eq(AdlsGen2CloudStorageV1Parameters.class));
    }

    @Test
    public void testConvertWhenTypeIsWasbThenExpectedWasbFileSystemShouldBeSet() throws IOException {
        when(fileSystem.getType()).thenReturn(WASB);
        WasbFileSystem wasb = mock(WasbFileSystem.class);
        when(configurations.get(WasbFileSystem.class)).thenReturn(wasb);
        WasbCloudStorageV1Parameters expected = mock(WasbCloudStorageV1Parameters.class);
        when(conversionService.convert(wasb, WasbCloudStorageV1Parameters.class)).thenReturn(expected);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertEquals(expected, result.getWasb());
        verify(conversionService, times(1)).convert(any(WasbFileSystem.class), eq(WasbCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(S3FileSystem.class), eq(S3CloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(GcsFileSystem.class), eq(GcsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsFileSystem.class), eq(AdlsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsGen2FileSystem.class), eq(AdlsGen2CloudStorageV1Parameters.class));
    }

    @Test
    public void testConvertWhenTypeIsAdlsGen2ThenExpectedAdlsGen2FileSystemShouldBeSet() throws IOException {
        when(fileSystem.getType()).thenReturn(ADLS_GEN_2);
        AdlsGen2FileSystem adlsGen2 = mock(AdlsGen2FileSystem.class);
        when(configurations.get(AdlsGen2FileSystem.class)).thenReturn(adlsGen2);
        AdlsGen2CloudStorageV1Parameters expected = mock(AdlsGen2CloudStorageV1Parameters.class);
        when(conversionService.convert(adlsGen2, AdlsGen2CloudStorageV1Parameters.class)).thenReturn(expected);

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertEquals(expected, result.getAdlsGen2());
        verify(conversionService, times(1)).convert(any(AdlsGen2FileSystem.class), eq(AdlsGen2CloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(WasbFileSystem.class), eq(WasbCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(S3FileSystem.class), eq(S3CloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(GcsFileSystem.class), eq(GcsCloudStorageV1Parameters.class));
        verify(conversionService, times(0)).convert(any(AdlsFileSystem.class), eq(AdlsCloudStorageV1Parameters.class));
    }

    @Test
    public void testConvertWhenGettingFileSystemFromConfigurationThrowsExceptionThenIWillBeCatchedAndNoFileSystemWillBeSet() throws IOException {
        // adls just for testing reason
        when(fileSystem.getType()).thenReturn(ADLS);
        when(configurations.get(any(Class.class))).thenThrow(new IOException("some message"));

        CloudStorageV4Request result = underTest.convert(fileSystem);

        assertNull(result.getAdls());
        assertNull(result.getGcs());
        assertNull(result.getS3());
        assertNull(result.getWasb());
        assertNull(result.getAdlsGen2());
    }

}