package com.smartmuseum.artinfo.seed;

import com.smartmuseum.artinfo.config.MuseumProperties;
import com.smartmuseum.artinfo.domain.Art;
import com.smartmuseum.artinfo.repository.ArtRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs once at application startup.
 * Seeds 100 artworks when MongoDB is empty.
 * Selects 100 random grid cells, so some cells may remain empty.
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

    private static final Pattern YEAR_PATTERN = Pattern.compile("(\\d{4})");

    private final ArtRepository    repository;
    private final MuseumProperties props;
    private final MongoTemplate mongoTemplate;
    private final Random           rng = new Random(42);

    public ArtSeeder(ArtRepository repository, MuseumProperties props, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.props      = props;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrateExistingDescriptionsToEnglish();

        if (repository.count() > 0) {
            log.info("Art data already exists, skipping seed");
            return;
        }

        int count = props.getArt().getSeedCount();
        int rows  = Math.max(1, props.getBuilding().getGridRows());
        int cols  = Math.max(1, props.getBuilding().getGridCols());
        int floors = Math.max(1, props.getBuilding().getFloors());

        // Build the list of all grid locations
        List<int[]> allGrids = new ArrayList<>();
        for (int f = 1; f <= floors; f++)
            for (int r = 0; r < rows; r++)
                for (int c = 0; c < cols; c++)
                    allGrids.add(new int[]{f, r, c});

        // Select 100 random grid locations
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
            String desc   = buildEnglishDescription(artist);

            arts.add(new Art(title, artist, desc, gridId, floor, col, row));
        }

        repository.saveAll(arts);
        log.info("Art seed complete: {} arts", arts.size());
    }

    private void migrateExistingDescriptionsToEnglish() {
        List<Art> allArts = repository.findAll();
        if (allArts.isEmpty()) {
            return;
        }

        for (Art art : allArts) {
            String description = art.getDescription();
            if (looksLikeMongolian(description)) {
                String englishDescription = buildEnglishDescription(art.getArtist(), description);
                Query query = Query.query(Criteria.where("_id").is(art.getId()));
                Update update = Update.update("description", englishDescription);
                mongoTemplate.updateFirst(query, update, Art.class);
                log.info("Migrated art description to English: {}", art.getId());
            }
        }
    }

    private boolean looksLikeMongolian(String description) {
        return description != null && (description.contains("бүтээл") || description.contains("онд") || description.contains("-н "));
    }

    private String buildEnglishDescription(String artist) {
        return buildEnglishDescription(artist, null);
    }

    private String buildEnglishDescription(String artist, String existingDescription) {
        int year = 1800 + rng.nextInt(200);
        if (existingDescription != null) {
            Matcher matcher = YEAR_PATTERN.matcher(existingDescription);
            if (matcher.find()) {
                year = Integer.parseInt(matcher.group(1));
            }
        }
        return "An artwork created in " + year + " by " + artist + ".";
    }
}