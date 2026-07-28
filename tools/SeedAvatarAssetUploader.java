import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

/**
 * Generates and uploads one deterministic 5x5 identicon for every legacy/V28/V36 seed user.
 *
 * <p>Run from the repository root after compiling with the application's Maven classpath.
 * Values in .env are read without printing credentials. Assets remain private and are served
 * through the application's existing presigned GET flow.</p>
 */
public final class SeedAvatarAssetUploader {

    private static final int V27_LEGACY_USER_COUNT = 184;
    private static final int V28_USER_COUNT = 184;
    private static final int V36_USER_COUNT = 100;

    private SeedAvatarAssetUploader() {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.awt.headless", "true");
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
            uploadCohort(s3, bucket, "v27-legacy", V27_LEGACY_USER_COUNT);
            uploadCohort(s3, bucket, "v28", V28_USER_COUNT);
            uploadCohort(s3, bucket, "v36", V36_USER_COUNT);
        }
    }

    private static void uploadCohort(S3Client s3, String bucket, String cohort, int count) throws IOException {
        for (int ordinal = 1; ordinal <= count; ordinal++) {
            String objectKey = "seed-assets/avatars/identicon-v1/" + cohort + "-" + String.format("%03d", ordinal) + ".png";
            s3.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey)
                            .contentType("image/png")
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(generateIdenticon("skillsprint-seed-identicon-v1:" + cohort + ":" + ordinal))
            );
            System.out.println("Uploaded " + objectKey);
        }
    }

    /** Creates an original GitHub-style mirrored 5x5 identicon from a stable hash. */
    private static byte[] generateIdenticon(String seed) throws IOException {
        byte[] hash = sha256(seed);
        int canvas = 512;
        int padding = 56;
        int cell = 80;
        BufferedImage image = new BufferedImage(canvas, canvas, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            float hue = unsigned(hash[0]) / 255.0f;
            Color background = Color.getHSBColor(hue, 0.13f, 0.98f);
            Color foreground = Color.getHSBColor(hue, 0.58f, 0.57f);
            Color accent = Color.getHSBColor((hue + 0.08f) % 1.0f, 0.72f, 0.72f);

            graphics.setColor(background);
            graphics.fillRect(0, 0, canvas, canvas);

            for (int row = 0; row < 5; row++) {
                for (int column = 0; column < 3; column++) {
                    int bit = row * 3 + column;
                    if (!isSet(hash, bit)) {
                        continue;
                    }
                    graphics.setColor(bit % 4 == 0 ? accent : foreground);
                    drawTile(graphics, padding + column * cell, padding + row * cell, cell);
                    if (column < 2) {
                        drawTile(graphics, padding + (4 - column) * cell, padding + row * cell, cell);
                    }
                }
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!javax.imageio.ImageIO.write(image, "png", output)) {
                throw new IOException("PNG writer is unavailable");
            }
            return output.toByteArray();
        }
    }

    private static void drawTile(Graphics2D graphics, int x, int y, int size) {
        graphics.fillRoundRect(x + 4, y + 4, size - 8, size - 8, 16, 16);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean isSet(byte[] hash, int bit) {
        return (unsigned(hash[bit / 8]) & (1 << (bit % 8))) != 0;
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
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
