package com.aquasense.user;

import com.aquasense.security.CurrentUser;
import com.aquasense.user.dto.UserResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Profile photo of the current user. Reading it also requires the session: photos are not public. */
@RestController
@RequestMapping("/api/v1/users/me/avatar")
public class AvatarController {

    private final AvatarService avatars;

    public AvatarController(AvatarService avatars) {
        this.avatars = avatars;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return UserResponse.from(avatars.replace(CurrentUser.id(), file.getBytes()));
    }

    @DeleteMapping
    public UserResponse remove() {
        return UserResponse.from(avatars.remove(CurrentUser.id()));
    }

    @GetMapping(produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image() {
        return avatars.find(CurrentUser.id())
            .map(a -> ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                // The client asks again with ?v=<version> when the photo changes, so a private cache is safe.
                .cacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate())
                .body(a.getImage()))
            .orElse(ResponseEntity.notFound().build());
    }
}
