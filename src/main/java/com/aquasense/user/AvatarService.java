package com.aquasense.user;

import com.aquasense.audit.AuditService;
import com.aquasense.common.BusinessException;
import com.aquasense.vision.ImageSanitizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profile photo. The server never keeps the uploaded file: it checks it (JPEG or PNG, size, dimensions declared
 * before decoding), crops the center square, reduces it to 256 px and re-encodes it as JPEG, which removes metadata
 * such as the GPS location. Demonstration accounts are shared, so their photo cannot be changed.
 */
@Service
public class AvatarService {

    static final int SIDE = 256;

    private final UserRepository users;
    private final UserAvatarRepository avatars;
    private final ImageSanitizer images;
    private final AuditService audit;

    public AvatarService(UserRepository users, UserAvatarRepository avatars, ImageSanitizer images, AuditService audit) {
        this.users = users;
        this.avatars = avatars;
        this.images = images;
        this.audit = audit;
    }

    @Transactional
    public User replace(Long userId, byte[] upload) {
        User user = editable(userId);
        byte[] image = images.squareJpeg(upload, SIDE);
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        avatars.findById(userId).ifPresentOrElse(a -> a.replace(image, now), () -> avatars.save(new UserAvatar(userId, image, now)));
        user.avatarChanged(now);
        audit.record(userId, "AVATAR_UPDATED", "Foto de " + SIDE + " × " + SIDE + " px");
        return user;
    }

    @Transactional
    public User remove(Long userId) {
        User user = editable(userId);
        if (avatars.existsById(userId)) {
            avatars.deleteById(userId);
            audit.record(userId, "AVATAR_REMOVED", "Foto de perfil quitada");
        }
        user.avatarChanged(null);
        return user;
    }

    @Transactional(readOnly = true)
    public Optional<UserAvatar> find(Long userId) {
        return avatars.findById(userId);
    }

    private User editable(Long userId) {
        User user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("La cuenta no existe."));
        if (user.isDemo()) {
            throw new BusinessException(HttpStatus.FORBIDDEN,
                "Las cuentas de demostración son compartidas y no pueden cambiar su foto. Crea tu propia cuenta para tener la tuya.");
        }
        return user;
    }
}
