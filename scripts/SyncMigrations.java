import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The tests build their H2 schema with the migrations of DBPatSft, the repository that owns the schema.
 * Run this after a new migration there: {@code java scripts/SyncMigrations.java ../DBPatSft}
 */
public class SyncMigrations {

    public static void main(String[] args) throws IOException {
        Path source = Path.of(args.length > 0 ? args[0] : "../DBPatSft").resolve("migrations");
        Path target = Path.of("src/test/resources/db/migration");
        if (!Files.isDirectory(source)) {
            System.err.println("No encuentro " + source);
            System.exit(1);
        }
        Files.createDirectories(target);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(target, "V*.sql")) {
            for (Path file : old) Files.delete(file);
        }
        int copied = 0;
        try (DirectoryStream<Path> migrations = Files.newDirectoryStream(source, "V*.sql")) {
            for (Path file : migrations) {
                Files.copy(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        }
        System.out.println("Copiadas " + copied + " migraciones desde " + source);
    }
}
