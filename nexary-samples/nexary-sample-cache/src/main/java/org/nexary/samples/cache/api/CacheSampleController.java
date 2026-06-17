package org.nexary.samples.cache.api;

import java.util.Map;
import org.nexary.samples.cache.service.UserProfileService;
import org.nexary.samples.cache.common.DeleteResult;
import org.nexary.samples.cache.common.LockResult;
import org.nexary.samples.cache.common.Profile;
import org.nexary.samples.cache.common.UserCount;
import org.nexary.samples.cache.common.WarmupResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP entry points for exercising the cache sample. */
@RestController
@RequestMapping("/examples/cache")
public class CacheSampleController {
    private final UserProfileService userProfileService;

    public CacheSampleController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/profiles/{id}")
    public Profile profile(@PathVariable String id) {
        return userProfileService.profile(id);
    }

    @PostMapping("/warmup")
    public WarmupResult warmup() {
        return userProfileService.warmup();
    }

    @GetMapping("/batch")
    public Map<String, Object> batch(@RequestParam(defaultValue = "101,102") String ids) {
        return userProfileService.batch(ids);
    }

    @DeleteMapping("/profiles/{id}")
    public DeleteResult delete(@PathVariable String id) {
        return userProfileService.delete(id);
    }

    @GetMapping("/user-counts/{userId}")
    public UserCount userCount(@PathVariable String userId) {
        return userProfileService.userCount(userId);
    }

    @PostMapping("/user-counts/{userId}/increments")
    public UserCount incrementUserCount(@PathVariable String userId, @RequestParam(defaultValue = "1") long delta) {
        return userProfileService.incrementUserCount(userId, delta);
    }

    @PostMapping("/user-counts/{userId}/decrements")
    public UserCount decrementUserCount(@PathVariable String userId, @RequestParam(defaultValue = "1") long delta) {
        return userProfileService.decrementUserCount(userId, delta);
    }

    @DeleteMapping("/user-counts/{userId}")
    public DeleteResult deleteUserCount(@PathVariable String userId) {
        return userProfileService.deleteUserCount(userId);
    }

    @PostMapping("/locks/{id}")
    public LockResult lock(@PathVariable String id) {
        return userProfileService.lock(id);
    }
}
