package org.openpass.common.security;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class PasswordHasher {
    private static final int ITERATIONS = 2;
    private static final int MEMORY_KB = 65536;
    private static final int PARALLELISM = 1;
    private static final int HASH_LENGTH = 32;
    private static final int SALT_LENGTH = 16;
    private final SecureRandom secureRandom = new SecureRandom();

    public record HashedResult(String hash, String salt) {}

    public HashedResult hashPassword(String password) {
        byte[] salt = new byte[SALT_LENGTH];
        secureRandom.nextBytes(salt);

        byte[] hash = computeArgon2id(password.toCharArray(), salt);
        String base64Salt = Base64.getEncoder().encodeToString(salt);
        String base64Hash = Base64.getEncoder().encodeToString(hash);

        return new HashedResult(base64Hash, base64Salt);
    }

    public boolean verifyPassword(String password, String storedHash, String storedSalt) {
        if (password == null || storedHash == null || storedSalt == null) {
            return false;
        }

        try {
            byte[] salt = Base64.getDecoder().decode(storedSalt);
            byte[] expectedHash = Base64.getDecoder().decode(storedHash);
            byte[] actualHash = computeArgon2id(password.toCharArray(), salt);

            return MessageDigest.isEqual(expectedHash, actualHash);
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] computeArgon2id(char[] password, byte[] salt) {
        Argon2Parameters.Builder builder = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withSalt(salt)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM);

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(builder.build());

        byte[] result = new byte[HASH_LENGTH];
        byte[] passwordBytes = new String(password).getBytes(StandardCharsets.UTF_8);
        generator.generateBytes(passwordBytes, result, 0, result.length);
        return result;
    }
}
