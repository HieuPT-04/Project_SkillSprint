import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Uploads the four generated seed-avatar assets used by V42.
 *
 * <p>Run from the repository root after compiling with the application's Maven classpath.
 * Values in .env are read without printing credentials. Assets remain private and are served
 * through the application's existing presigned GET flow.</p>
 */
public final class SeedAvatarAssetUploader {

    private static final Path ASSET_DIRECTORY = Path.of("src/main/resources/seed-assets/avatars");
    private static final String[] ASSET_NAMES = {
            "avatar-01.png", "avatar-02.png", "avatar-03.png", "avatar-04.png"
    };

    private SeedAvatarAssetUploader() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> settings = new HashMap<>(System.getenv());
        loadDotEnv(settings, Path.of(".env"));

        String region = required(settings, "AWS_REGION");
        String bucket = required(settings, "AWS_S3_BUCKET");
        String accessKeyId = required(settings, "AWS_ACCESS_KEY_ID");
        String secretAccessKey = required(settings, "AWS_SECRET_ACCESS_KEY");

        try (S3Client s3 = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKeyId, secretAccessKey)
                ))
                .build()) {
            for (String assetName : ASSET_NAMES) {
                Path asset = ASSET_DIRECTORY.resolve(assetName);
                if (!Files.isRegularFile(asset)) {
                    throw new IllegalStateException("Missing seed avatar asset: " + asset);
                }

                String objectKey = "seed-assets/avatars/" + assetName;
                s3.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .contentType("image/png")
                                .cacheControl("public, max-age=31536000, immutable")
                                .build(),
                        asset
                );
                System.out.println("Uploaded " + objectKey);
            }
        }
    }

    private static void loadDotEnv(Map<String, String> settings, Path envFile) throws IOException {
        if (!Files.isRegularFile(envFile)) {
            return;
        }

        for (String line : Files.readAllLines(envFile)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            settings.putIfAbsent(key, unquote(value));
        }
    }

    private static String required(Map<String, String> settings, String key) {
        String value = settings.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required S3 setting: " + key);
        }
        return value;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
