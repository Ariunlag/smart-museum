package com.smartmuseum.artinfo.service;

import com.smartmuseum.artinfo.config.MuseumProperties;
import com.smartmuseum.artinfo.domain.Art;
import com.smartmuseum.artinfo.repository.ArtRepository;
import com.smartmuseum.artinfo.web.dto.ArtInfoResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ArtInfoServiceTest {

    private ArtRepository repository;
    private ArtInfoService service;

    @BeforeEach
    void setUp() {
        repository = mock(ArtRepository.class);

        MuseumProperties props = new MuseumProperties();
        props.getArt().setNearbyRange(2);
        props.getArt().setMaxResults(2);

        service = new ArtInfoService(repository, props);
    }

    @Test
    void findNearest_shouldFilterSortAndLimitByManhattanDistance() {
        Art close = mockArt("a1", "Close", 1, 0);
        Art closer = mockArt("a2", "Closer", 0, 0);
        Art far = mockArt("a3", "Far", 8, 8);

        when(repository.findByFloorId(2)).thenReturn(List.of(close, far, closer));

        ArtInfoResponse result = service.findNearest("A1", 2);

        assertEquals(2, result.arts().size());
        assertEquals("a2", result.arts().get(0).artId());
        assertEquals("a1", result.arts().get(1).artId());
    }

    @Test
    void findNearest_shouldThrowForMalformedGridId() {
        when(repository.findByFloorId(1)).thenReturn(List.of());
        assertThrows(NumberFormatException.class, () -> service.findNearest("AB", 1));
    }

    @Test
    void findById_shouldMapEntityToDto() {
        Art art = mockArt("art-1", "Mona", 0, 0);
        when(repository.findById("art-1")).thenReturn(Optional.of(art));

        ArtInfoResponse.ArtDto dto = service.findById("art-1");

        assertEquals("art-1", dto.artId());
        assertEquals("Mona", dto.title());
    }

    @Test
    void findById_shouldThrowWhenNotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.findById("missing"));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void findAll_shouldReturnAllMappedDtos() {
        Art a1 = mockArt("id-1", "T1", 0, 0);
        Art a2 = mockArt("id-2", "T2", 1, 1);
        when(repository.findAll()).thenReturn(List.of(a1, a2));

        List<ArtInfoResponse.ArtDto> dtos = service.findAll();

        assertEquals(2, dtos.size());
        assertEquals("id-1", dtos.get(0).artId());
        assertEquals("id-2", dtos.get(1).artId());
    }

    private Art mockArt(String id, String title, int x, int y) {
        Art art = mock(Art.class);
        when(art.getId()).thenReturn(id);
        when(art.getTitle()).thenReturn(title);
        when(art.getArtist()).thenReturn("artist");
        when(art.getDescription()).thenReturn("desc");
        when(art.getX()).thenReturn(x);
        when(art.getY()).thenReturn(y);
        return art;
    }
}
