package com.quietmetrix.dashboard.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Task 4 — new models deserialize the backends' snake_case JSON. */
class ApiModelsTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun projectDecodesApiKeyLast4() {
        val p = json.decodeFromString<ApiProject>(
            """{"id":"p1","name":"App","plan_id":null,"api_key_last4":"abcd","created_at":"2026-01-01"}"""
        )
        assertEquals("abcd", p.apiKeyLast4)
    }

    @Test
    fun regenerateResponseDecodes() {
        val r = json.decodeFromString<RegenerateKeyResponse>(
            """{"api_key":"qm_secret_xyz","api_key_last4":"_xyz"}"""
        )
        assertEquals("qm_secret_xyz", r.apiKey)
        assertEquals("_xyz", r.apiKeyLast4)
    }

    @Test
    fun usersResponseDecodes() {
        val u = json.decodeFromString<UsersResponse>(
            """{"users":[{"id":"u1","email":"a@b.c","role":"developer","status":"invited","created_at":"x"}]}"""
        )
        assertEquals(1, u.users.size)
        assertEquals("developer", u.users[0].role)
        assertEquals("invited", u.users[0].status)
    }

    @Test
    fun inviteResponseDecodesLink() {
        val r = json.decodeFromString<InviteResponse>(
            """{"invite_link":"https://x/?invite=tok","user":{"id":"u1","email":"a@b.c","role":"reviewer","status":"invited"}}"""
        )
        assertEquals("https://x/?invite=tok", r.inviteLink)
        assertEquals("reviewer", r.user?.role)
    }

    @Test
    fun membersResponseDecodes() {
        val m = json.decodeFromString<MembersResponse>(
            """{"members":[{"user_id":"u9","email":"dev@x.c","user_role":"developer","member_role":"viewer"}]}"""
        )
        assertEquals("u9", m.members[0].userId)
        assertEquals("dev@x.c", m.members[0].email)
    }

    @Test
    fun invitePreviewDecodes() {
        assertEquals("a@b.c", json.decodeFromString<InvitePreview>("""{"email":"a@b.c"}""").email)
    }

    @Test
    fun projectWithoutLast4IsNull() {
        val p = json.decodeFromString<ApiProject>("""{"id":"p1","name":"App"}""")
        assertNull(p.apiKeyLast4)
    }

    @Test
    fun transitionsTolerateNullScreens() {
        // A transition into/out of an unnamed screen must not crash deserialization.
        val r = json.decodeFromString<TransitionsResponse>(
            """{"transitions":[{"from_screen":"Home","to_screen":null,"count":4}]}"""
        )
        assertEquals(1, r.transitions.size)
        assertEquals("Home", r.transitions[0].fromScreen)
        assertNull(r.transitions[0].toScreen)
        assertEquals(4L, r.transitions[0].count)
    }

    @Test
    fun aggregatesDecodeScreenDurations() {
        val r = json.decodeFromString<AggregatesResponse>(
            """{"screen_durations":[{"screen":"Home","count":12,"avg_ms":8200,"total_ms":98400}]}"""
        )
        assertEquals(1, r.screenDurations.size)
        val s = r.screenDurations[0]
        assertEquals("Home", s.screen)
        assertEquals(12L, s.count)
        assertEquals(8200L, s.avgMs)
        assertEquals(98400L, s.totalMs)
    }

    @Test
    fun aggregatesWithoutScreenDurationsDefaultsToEmpty() {
        val r = json.decodeFromString<AggregatesResponse>("""{"top_screens":[]}""")
        assertEquals(0, r.screenDurations.size)
    }

    @Test
    fun totalsDecodesErrorsAndDefaultsToZero() {
        val withErrors = json.decodeFromString<Totals>("""{"events":10,"offline":2,"errors":3}""")
        assertEquals(3L, withErrors.errors)
        val legacy = json.decodeFromString<Totals>("""{"events":10,"offline":2}""")
        assertEquals(0L, legacy.errors)
    }

    @Test
    fun funnelDtoDecodesStepsAndFlags() {
        val f = json.decodeFromString<FunnelDto>(
            """{"funnel_key":"signup","name":"Signup","description":null,
                "steps":[{"key":"view","event":"screen_view","screen":"signup"},
                         {"key":"submit","event":"signup_submitted"}],
                "window_seconds":3600,"source":"sdk","locked":true,
                "created_at":"2026-01-01T00:00:00Z","updated_at":"2026-01-02T00:00:00Z"}"""
        )
        assertEquals("signup", f.funnelKey)
        assertEquals(2, f.steps.size)
        assertEquals("screen_view", f.steps[0].event)
        assertEquals("signup", f.steps[0].screen)
        assertEquals(3600L, f.windowSeconds)
        assertEquals("sdk", f.source)
        assertEquals(true, f.locked)
    }

    @Test
    fun funnelsResponseDefaultsToEmptyList() {
        val r = json.decodeFromString<FunnelsResponse>("""{}""")
        assertEquals(0, r.funnels.size)
    }

    @Test
    fun funnelStepWithoutOptionalFieldsDecodes() {
        val s = json.decodeFromString<FunnelStepDto>("""{"key":"a","event":"e"}""")
        assertNull(s.screen)
        assertNull(s.name)
        assertEquals(0, s.props.size)
    }

    @Test
    fun funnelResultsResponseDecodesCoreNumbers() {
        val r = json.decodeFromString<FunnelResultsResponse>(
            """{"funnel":{"funnel_key":"signup","name":"Signup","window_seconds":3600,"steps":[]},
                "range":{"from":"2026-01-01T00:00:00Z","to":"2026-01-02T00:00:00Z"},
                "counted_by":"install","entered":10,"converted":4,"overall_conversion":0.4,
                "median_total_ms":12000,
                "steps":[{"key":"view","count":10,"conversion_from_entry":1.0,
                          "conversion_from_previous":1.0,"dropped":0,"drop_rate":0.0}],
                "breakdown":null,"trend":null,"truncated":false}"""
        )
        assertEquals("signup", r.funnel.funnelKey)
        assertEquals("install", r.countedBy)
        assertEquals(10, r.entered)
        assertEquals(4, r.converted)
        assertEquals(0.4, r.overallConversion)
        assertEquals(12000L, r.medianTotalMs)
        assertEquals(1, r.steps.size)
        assertNull(r.breakdown)
        assertNull(r.trend)
    }

    @Test
    fun funnelResultsResponseDecodesBreakdownAndTrend() {
        val r = json.decodeFromString<FunnelResultsResponse>(
            """{"funnel":{"funnel_key":"signup","name":"Signup","window_seconds":3600,"steps":[]},
                "range":{"from":"x","to":"y"},"counted_by":"mixed","entered":3,"converted":1,
                "overall_conversion":0.33,"steps":[],
                "breakdown":{"dimension":"platform","values":[
                    {"value":"android","entered":2,"overall_conversion":0.5,"steps":[]}
                ]},
                "trend":[{"bucket":"2026-01-01","entered":3,"converted":1,"conversion":0.33}],
                "truncated":true}"""
        )
        assertEquals("platform", r.breakdown?.dimension)
        assertEquals("android", r.breakdown?.values?.get(0)?.value)
        assertEquals(1, r.trend?.size)
        assertEquals("2026-01-01", r.trend?.get(0)?.bucket)
        assertEquals(true, r.truncated)
    }

    // --- Access tokens (Task 6) --------------------------------------------------------------

    @Test
    fun accessTokenDtoDecodesScopesAndTimestamps() {
        val t = json.decodeFromString<AccessTokenDto>(
            """{"id":"1","name":"agent","last4":"a71b","scopes":["projects:create"],
                "created_at":"2026-08-16T00:00:00","expires_at":null,"last_used_at":null}"""
        )
        assertEquals("agent", t.name)
        assertEquals("a71b", t.last4)
        assertEquals(listOf("projects:create"), t.scopes)
        assertNull(t.expiresAt)
    }

    @Test
    fun accessTokensResponseDefaultsToEmptyList() {
        val r = json.decodeFromString<AccessTokensResponse>("""{}""")
        assertEquals(0, r.tokens.size)
    }

    @Test
    fun createTokenResponseDecodesPlaintextToken() {
        val r = json.decodeFromString<CreateTokenResponse>(
            """{"id":"1","name":"agent","token":"qm_pat_abc123","last4":"c123",
                "scopes":["projects:create"],"created_at":"2026-08-16T00:00:00"}"""
        )
        assertEquals("qm_pat_abc123", r.token)
        assertEquals("c123", r.last4)
    }

    @Test
    fun createTokenRequestRoundTripsWithoutOptionalFields() {
        val encoded = json.encodeToString(CreateTokenRequest.serializer(), CreateTokenRequest("agent"))
        val decoded = json.decodeFromString<CreateTokenRequest>(encoded)
        assertEquals("agent", decoded.name)
        assertNull(decoded.scopes)
        assertNull(decoded.expiresInDays)
    }
}
