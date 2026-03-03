package com.smartmuseum.artinfo.seed;

import com.smartmuseum.artinfo.config.MuseumProperties;
import com.smartmuseum.artinfo.domain.Art;
import com.smartmuseum.artinfo.repository.ArtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * App эхлэхэд нэг удаа ажиллана.
 * MongoDB хоосон бол 100 art үүсгэж хадгална.
 * Grid-үүдээс random 100-г сонгоно — зарим grid хоосон үлдэнэ.
 */
@Component
public class ArtSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ArtSeeder.class);

    private static final String[] TITLES = {
        "Starry Night", "Mona Lisa", "The Scream", "Guernica", "The Persistence of Memory",
        "Girl with a Pearl Earring", "The Birth of Venus", "The Last Supper", "Sunflowers",
        "Water Lilies", "The Kiss", "American Gothic", "Grant Wood", "Nighthawks",
        "A Sunday on La Grande Jatte", "Whistler's Mother", "The Night Watch",
        "Las Meninas", "The Arnolfini Portrait", "A Bar at the Folies-Bergère"
    };

    private static final String[] ARTISTS = {
        "Vincent van Gogh", "Leonardo da Vinci", "Edvard Munch", "Pablo Picasso",
        "Salvador Dalí", "Johannes Vermeer", "Sandro Botticelli", "Rembrandt",
        "Claude Monet", "Gustav Klimt", "Edward Hopper", "Georges Seurat",
        "James McNeill Whistler", "Diego Velázquez", "Jan van Eyck", "Édouard Manet"
    };

    private final ArtRepository    repository;
    private final MuseumProperties props;
    private final Random           rng = new Random(42);

    public ArtSeeder(ArtRepository repository, MuseumProperties props) {
        this.repository = repository;
        this.props      = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("Art data already exists, skipping seed");
            return;
        }

        int count = props.getArt().getSeedCount();
        int rows  = 10;
        int cols  = 10;
        int floors = 3;

        // Grid-үүдийн жагсаалт үүсгэнэ
        List<int[]> allGrids = new ArrayList<>();
        for (int f = 1; f <= floors; f++)
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    allGrids.add(new int[]{f, r, c});

        // Random 100 grid сонгоно
        Collections.shuffle(allGrids, rng);
        List<int[]> selected = allGrids.subList(0, Math.min(count, allGrids.size()));

        List<Art> arts = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            int[] g      = selected.get(i);
            int floor    = g[0];
            int row      = g[1];
            int col      = g[2];
            String gridId = "" + (char)('A' + row) + (col + 1);

            String title  = TITLES[i % TITLES.length] + " " + (i + 1);
            String artist = ARTISTS[i % ARTISTS.length];
            String desc   = artist + "-н " + (1800 + rng.nextInt(200)) + " онд бүтээсэн бүтээл.";

            arts.add(new Art(title, artist, desc, gridId, floor, col, row));
        }

        repository.saveAll(arts);
        log.info("Art seed complete: {} arts", arts.size());
    }
}