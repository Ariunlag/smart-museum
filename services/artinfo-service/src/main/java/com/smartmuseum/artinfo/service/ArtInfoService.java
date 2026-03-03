package com.smartmuseum.artinfo.service;

import com.smartmuseum.artinfo.config.MuseumProperties;
import com.smartmuseum.artinfo.domain.Art;
import com.smartmuseum.artinfo.repository.ArtRepository;
import com.smartmuseum.artinfo.web.dto.ArtInfoResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ArtInfoService {

    private final ArtRepository    repository;
    private final MuseumProperties props;

    public ArtInfoService(ArtRepository repository, MuseumProperties props) {
        this.repository = repository;
        this.props      = props;
    }

    /**
     * gridId + floorId-р ойролцоох art хайна.
     *
     * 1. Яг тэр grid-т art байвал буцаана
     * 2. nearbyRange зай дотор хайна (manhattan distance)
     * 3. maxResults тоогоор хязгаарлана
     */
    public ArtInfoResponse findNearest(String gridId, int floorId) {
        int range      = props.getArt().getNearbyRange();
        int maxResults = props.getArt().getMaxResults();

        // GridId → x, y
        int queryX = gridId.length() > 1 ? Integer.parseInt(gridId.substring(1)) - 1 : 0;
        int queryY = gridId.length() > 0 ? gridId.charAt(0) - 'A' : 0;

        // Тухайн давхарын бүх art авна
        List<Art> floorArts = repository.findByFloorId(floorId);

        // Manhattan distance-р эрэмбэлнэ
        List<Art> nearest = floorArts.stream()
                .filter(a -> manhattanDist(a.getX(), a.getY(), queryX, queryY) <= range)
                .sorted(Comparator.comparingInt(
                        a -> manhattanDist(a.getX(), a.getY(), queryX, queryY)))
                .limit(maxResults)
                .toList();

        List<ArtInfoResponse.ArtDto> dtos = nearest.stream()
                .map(a -> new ArtInfoResponse.ArtDto(
                        a.getId(),
                        a.getTitle(),
                        a.getArtist(),
                        a.getDescription()
                ))
                .toList();

        return new ArtInfoResponse(dtos);
    }

    private int manhattanDist(int x1, int y1, int x2, int y2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2);
    }
}