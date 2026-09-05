package com.runetags.quickprofile;

import com.runetags.model.PlayerAccountType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.extern.slf4j.Slf4j;

import net.runelite.api.Experience;
import net.runelite.client.hiscore.HiscoreClient;
import net.runelite.client.hiscore.HiscoreEndpoint;
import net.runelite.client.hiscore.HiscoreResult;
import net.runelite.client.hiscore.HiscoreSkill;
import net.runelite.client.hiscore.HiscoreSkillType;
import net.runelite.client.hiscore.Skill;

@Slf4j
public class HiscoreEnrichmentService
{
    private final HiscoreClient hiscoreClient;
    private final ProfileEnrichmentCache cache;

    private CompletableFuture<PlayerAccountType> detectAccountType(String playerName)
    {
        /*
         * Official individual HiScore endpoints can positively identify the
         * three solo Ironman variants.
         *
         * If a player is absent from the solo-Ironman tables, RuneTags cannot
         * safely distinguish NORMAL from Group Ironman using this API alone.
         * Return UNKNOWN and allow native chat observations to resolve it later.
         */
        return safeLookup(
                playerName,
                HiscoreEndpoint.IRONMAN)
                .thenCompose(
                        ironmanResult ->
                        {
                            if (ironmanResult == null)
                            {
                                return CompletableFuture.completedFuture(
                                        PlayerAccountType.UNKNOWN);
                            }

                            final CompletableFuture<HiscoreResult> hardcoreFuture =
                                    safeLookup(
                                            playerName,
                                            HiscoreEndpoint.HARDCORE_IRONMAN);

                            final CompletableFuture<HiscoreResult> ultimateFuture =
                                    safeLookup(
                                            playerName,
                                            HiscoreEndpoint.ULTIMATE_IRONMAN);

                            return hardcoreFuture.thenCombine(
                                    ultimateFuture,
                                    (hardcoreResult, ultimateResult) ->
                                    {
                                        if (hardcoreResult != null)
                                        {
                                            return PlayerAccountType.HARDCORE;
                                        }

                                        if (ultimateResult != null)
                                        {
                                            return PlayerAccountType.ULTIMATE;
                                        }

                                        return PlayerAccountType.IRONMAN;
                                    });
                        });
    }

    private CompletableFuture<HiscoreResult> safeLookup(
            String playerName,
            HiscoreEndpoint endpoint)
    {
        try
        {
            final CompletableFuture<HiscoreResult> future =
                    hiscoreClient.lookupAsync(
                            playerName,
                            endpoint);

            if (future == null)
            {
                return CompletableFuture.completedFuture(
                        null);
            }

            return future.handle(
                    (result, throwable) ->
                            throwable == null
                                    ? result
                                    : null);
        }
        catch (RuntimeException ex)
        {
            return CompletableFuture.completedFuture(
                    null);
        }
    }

    @lombok.Value
    private static class BaseLookupResult
    {
        HiscoreResult result;
        Throwable throwable;
    }

    private static Integer calculateCombatLevel(
            HiscoreResult result)
    {
        final Skill attack =
                result.getSkill(HiscoreSkill.ATTACK);

        final Skill strength =
                result.getSkill(HiscoreSkill.STRENGTH);

        final Skill defence =
                result.getSkill(HiscoreSkill.DEFENCE);

        final Skill hitpoints =
                result.getSkill(HiscoreSkill.HITPOINTS);

        final Skill magic =
                result.getSkill(HiscoreSkill.MAGIC);

        final Skill ranged =
                result.getSkill(HiscoreSkill.RANGED);

        final Skill prayer =
                result.getSkill(HiscoreSkill.PRAYER);

        if (attack == null
                || strength == null
                || defence == null
                || hitpoints == null
                || magic == null
                || ranged == null
                || prayer == null)
        {
            return null;
        }

        if (attack.getLevel() < 1
                || strength.getLevel() < 1
                || defence.getLevel() < 1
                || hitpoints.getLevel() < 1
                || magic.getLevel() < 1
                || ranged.getLevel() < 1
                || prayer.getLevel() < 1)
        {
            return null;
        }

        return Experience.getCombatLevel(
                attack.getLevel(),
                strength.getLevel(),
                defence.getLevel(),
                hitpoints.getLevel(),
                magic.getLevel(),
                ranged.getLevel(),
                prayer.getLevel());
    }

    public HiscoreEnrichmentService(
            HiscoreClient hiscoreClient,
            ProfileEnrichmentCache cache)
    {
        this.hiscoreClient = hiscoreClient;
        this.cache = cache;
    }

    public EnrichmentRequest enrich(
            String playerName)
    {
        final ProfileEnrichmentCache.CachedProfileEnrichment cached =
                cache.get(playerName)
                        .orElse(null);

        if (cached != null)
        {
            return EnrichmentRequest.cached(
                    cached);
        }

        final CompletableFuture<HiscoreResult> normalFuture;

        try
        {
            normalFuture =
                    hiscoreClient.lookupAsync(
                            playerName,
                            HiscoreEndpoint.NORMAL);
        }
        catch (RuntimeException ex)
        {
            log.debug(
                    "[RuneTags] Unable to start HiScore-Lookup for Player='{}'",
                    playerName,
                    ex);

            return EnrichmentRequest.failed();
        }

        if (normalFuture == null)
        {
            return EnrichmentRequest.failed();
        }

        final CompletableFuture<PlayerAccountType> accountTypeFuture =
                detectAccountType(
                        playerName);

        final CompletableFuture<ProfileEnrichmentCache.CachedProfileEnrichment> mapped =
                normalFuture.handle(
                                (result, throwable) ->
                                {
                                    if (throwable != null)
                                    {
                                        log.debug(
                                                "[RuneTags] HiScore-Lookup failed for Player='{}' | ERROR: {}",
                                                playerName,
                                                throwable.getMessage());

                                        return new BaseLookupResult(
                                                null,
                                                throwable);
                                    }

                                    return new BaseLookupResult(
                                            result,
                                            null);
                                })
                        .thenCombine(
                                accountTypeFuture,
                                (baseLookup, accountType) ->
                                {
                                    if (baseLookup.getThrowable() != null)
                                    {
                                        return new ProfileEnrichmentCache.CachedProfileEnrichment(
                                                ProfileEnrichmentState.ERROR,
                                                null);
                                    }

                                    final HiscoreResult result =
                                            baseLookup.getResult();

                                    if (result == null)
                                    {
                                        cache.putNotFound(
                                                playerName);

                                        return new ProfileEnrichmentCache.CachedProfileEnrichment(
                                                ProfileEnrichmentState.NOT_FOUND,
                                                null);
                                    }

                                    final Skill overall =
                                            result.getSkill(
                                                    HiscoreSkill.OVERALL);

                                    final Integer totalLevel =
                                            overall != null
                                                    && overall.getLevel() >= 0
                                                    ? overall.getLevel()
                                                    : null;

                                    final Integer combatLevel =
                                            calculateCombatLevel(
                                                    result);

                                    final Map<String, Integer> contextValues =
                                            new LinkedHashMap<>();

                                    for (HiscoreSkill hiscoreSkill
                                            : HiscoreSkill.values())
                                    {
                                        if (hiscoreSkill.getType()
                                                != HiscoreSkillType.BOSS
                                                && hiscoreSkill.getType()
                                                != HiscoreSkillType.ACTIVITY
                                                && hiscoreSkill.getType()
                                                != HiscoreSkillType.SKILL)
                                        {
                                            continue;
                                        }

                                        final Skill skill =
                                                result.getSkill(
                                                        hiscoreSkill);

                                        if (skill == null
                                                || skill.getLevel() < 0)
                                        {
                                            continue;
                                        }

                                        contextValues.put(
                                                hiscoreSkill.name(),
                                                skill.getLevel());
                                    }

                                    final HiscoreProfileData data =
                                            new HiscoreProfileData(
                                                    combatLevel,
                                                    totalLevel,
                                                    accountType,
                                                    contextValues);

                                    cache.putSuccess(
                                            playerName,
                                            data);

                                    return new ProfileEnrichmentCache.CachedProfileEnrichment(
                                            ProfileEnrichmentState.LOADED,
                                            data);
                                });

        return EnrichmentRequest.loading(
                mapped);
    }

    @lombok.Value
    public static class EnrichmentRequest
    {
        ProfileEnrichmentState initialState;
        HiscoreProfileData initialData;
        CompletableFuture<ProfileEnrichmentCache.CachedProfileEnrichment> future;

        static EnrichmentRequest cached(
                ProfileEnrichmentCache.CachedProfileEnrichment cached)
        {
            return new EnrichmentRequest(
                    cached.getState(),
                    cached.getData(),
                    null);
        }

        static EnrichmentRequest loading(
                CompletableFuture<ProfileEnrichmentCache.CachedProfileEnrichment> future)
        {
            return new EnrichmentRequest(
                    ProfileEnrichmentState.LOADING,
                    null,
                    future);
        }

        static EnrichmentRequest failed()
        {
            return new EnrichmentRequest(
                    ProfileEnrichmentState.ERROR,
                    null,
                    null);
        }
    }
}
