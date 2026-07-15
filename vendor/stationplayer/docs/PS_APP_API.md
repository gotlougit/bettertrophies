# PlayStation App API Reference

This document describes the reverse-engineered PlayStation App APIs that this project
uses or has confirmed working via live probes. Every endpoint listed here has been
verified against actual Sony infrastructure (production `np` environment unless noted).

---

## Credentials

The Android app ships with these OAuth client credentials:

| Field | Value |
|---|---|
| `client_id` | `09515159-7237-4370-9b40-3806e67c0891` |
| `client_secret` | `ucPjka5tntB2KqsP` |
| `redirect_uri` | `com.scee.psxandroid.scecompcall://redirect` |
| `scope` | `psn:mobile.v2.core psn:clientapp` |
| `service_entity` | `urn:service-entity:psn` |

The client mimics a `PlayStationApp-Android/26.4.0` device.

---

## 1. Authentication

### 1.1 Authorize URL (WebView flow)

The app opens this URL in a WebView to let the user sign in:

```
GET https://ca.account.sony.com/api/authz/v3/oauth/authorize
    ?client_id=09515159-7237-4370-9b40-3806e67c0891
    &redirect_uri=com.scee.psxandroid.scecompcall://redirect
    &response_type=code
    &scope=psn%3Amobile.v2.core+psn%3Aclientapp
    &service_entity=urn%3Aservice-entity%3Apsn
    &prompt=login
```

After the user logs in, the WebView redirects to the `redirect_uri` with either:

- A `code` query parameter (authorization code), or
- An `npsso` cookie set on the `account.sony.com` domain.

This project uses the `npsso` cookie path because it doesn't require a browser callback.

### 1.2 Token Exchange (npsso → access token)

```
POST https://ca.account.sony.com/api/authz/v3/oauth/token
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)
```

Body (form-encoded):

| Param | Value |
|---|---|
| `token_format` | `jwt` |
| `grant_type` | `sso_token` |
| `npsso` | The `npsso` cookie value |
| `scope` | `psn:mobile.v2.core psn:clientapp` |
| `service_entity` | `urn:service-entity:psn` |

Response (JSON):

```json
{
  "access_token": "eyJ...",
  "token_type": "bearer",
  "scope": "psn:mobile.v2.core psn:clientapp",
  "expires_in": 3600,
  "refresh_token": "...",
  "id_token": "..."
}
```

The `access_token` is used for all subsequent API calls. The `refresh_token`
can be exchanged for a new access token using `grant_type=refresh_token`.

### 1.3 Refresh Token

```
POST https://ca.account.sony.com/api/authz/v3/oauth/token
Authorization: Basic base64(client_id:client_secret)
```

Form body: `token_format=jwt&grant_type=refresh_token&scope=...&refresh_token=...`

---

## 2. Account Bootstrap

### 2.1 Get Account ID (DMS)

```
GET https://dms.api.playstation.com/api/v1/devices/accounts/me
Authorization: Bearer {access_token}
```

Response (JSON):

```json
{
  "accountId": "1234567890123456789",
  "accountDevices": [...]
}
```

`accountId` is the 19-digit PSN account ID used in nearly every other endpoint.

---

## 3. Trophy API

Base: `https://m.np.playstation.com/api/trophy/v1`

All trophy endpoints use bearer auth with standard mobile headers
(`User-Agent: okhttp/4.12.0`, `Accept-Language: en-US`, `Accept-Encoding: gzip`,
along with `x-psn-request-id`, `x-psn-app-ver`, `x-psn-trace-id`, `x-psn-span-id`,
`x-psn-sampled: 0`, `If-Modified-Since`).

### 3.1 Trophy Summary

```
GET /api/trophy/v1/users/{accountId}/trophySummary
```

Returns account-level trophy totals, level, and tier.

Response keys: `accountId`, `trophyLevel`, `tier`, `progress`, `trophyPoint`,
`earnedTrophies` (breakdown of bronze/silver/gold/platinum).

### 3.2 Trophy Titles

```
GET /api/trophy/v1/users/{accountId}/trophyTitles?limit=100&offset=0
```

Paginated list of every game the account has earned trophies in. Response has
`nextOffset`, `totalItemCount`, and `trophyTitles[]`.

Each title entry has: `npCommunicationId`, `npServiceName`, `npTitleId`,
`trophyTitleName`, `trophyTitlePlatform`, `trophyTitleIconUrl`, `definedTrophies`,
`earnedTrophies`, `progress`, `trophySetVersion`, `hasTrophyGroups`,
`trophyGroupCount`, `lastUpdatedDateTime`.

### 3.3 Trophy Title Lookup (by NP Title ID)

```
GET /api/trophy/v1/users/{accountId}/titles/trophyTitles
    ?includeNotEarnedTrophyIds=true
    &npTitleIds={id1},{id2}
```

Returns trophy metadata for specific title IDs, including `npCommunicationId`
and `npServiceName` fields needed for deeper trophy queries. Also returns
`notEarnedTrophyIds` and `rarestTrophies` when available.

### 3.4 Trophy Groups

```
GET /api/trophy/v1/users/{accountId}/npCommunicationIds/{npCommId}/trophyGroups
    ?npServiceName={serviceName}
```

Returns how a game's trophies are divided into groups (base game, DLC packs, etc.).
Response contains `trophyGroups[]` with `groupId`, `groupName`, `trophyGroupIconUrl`,
`definedTrophies`, `earnedTrophies`, `progress`.

`npServiceName` defaults to `"trophy"` but some titles use `"trophy2"` or
`"concepts"`. This value is obtained from the trophy title listing (3.2 or 3.3).

### 3.5 Trophies in a Group (User)

```
GET /api/trophy/v1/users/{accountId}/npCommunicationIds/{npCommId}/trophyGroups/{groupId}/trophies
    ?npServiceName={serviceName}
```

Returns the user's progress on every trophy in a group. Each trophy has:
`trophyId`, `earned`, `earnedDateTime`, `progress`, `progressRate`,
`progressedDateTime`.

### 3.6 Trophies in a Group (Definition)

```
GET /api/trophy/v1/npCommunicationIds/{npCommId}/trophyGroups/{groupId}/trophies
    ?npServiceName={serviceName}
```

Returns the canonical definition of every trophy in a group (name, description,
type, rarity, icon). No account ID in the URL — this is a public definition
endpoint. Each trophy has: `trophyId`, `trophyName`, `trophyDetail`,
`trophyType`, `trophyIconUrl`, `trophyRare`, `trophyEarnedRate`.

> Merging 3.5 + 3.6 gives a full picture: user state (earned or not) joined
> with definition data.

### 3.7 Single Trophy Detail (User)

```
GET /api/trophy/v1/users/{accountId}/npCommunicationIds/{npCommId}/trophies/{trophyId}
    ?npServiceName={serviceName}
```

### 3.8 Single Trophy Detail (Definition)

```
GET /api/trophy/v1/npCommunicationIds/{npCommId}/trophies/{trophyId}
    ?npServiceName={serviceName}
```

### 3.9 Trophy Appearance Setting

```
GET /api/trophy/v1/users/me/npCommunicationIds/{npCommId}/appearanceSetting
    ?npServiceName={serviceName}
```

Returns per-title trophy visibility config. Response: `visibility`,
`revealAllTrophies`, `canDisplayAllTrophies`, `isSet`.

---

## 4. Profile API

### 4.1 Get Own Profile

```
GET /api/userProfile/v1/internal/users/{accountId}/profiles
Authorization: Bearer {access_token}
```

Returns the signed-in user's profile card:

```json
{
  "onlineId": "player_name",
  "aboutMe": "...",
  "avatars": [{"size": "l", "url": "https://..."}],
  "languages": ["en"],
  "isOfficiallyVerified": false,
  "isPlus": true,
  "isMe": true,
  "personalDetail": {"firstName": "...", "lastName": "..."}
}
```

---

## 5. Game Media Service (Cloud Media Gallery)

Base: `https://m.np.playstation.com/api/gameMediaService/v2/c2s`

All endpoints use bearer auth with standard mobile headers.

### 5.1 List Captures

```
GET /api/gameMediaService/v2/c2s/category/cloudMediaGallery/ugcType/all
    ?includeTokenizedUrls=true
    &limit=100
    &cursorMark={cursor}
```

Paginates through all cloud media captures for the account. Response has
`ugcDocument[]` (each a `CloudMediaCapture`), `nextCursorMark`, `limit`.

Each capture has: `id` (UGC ID), `sceTitleId`, `sceTitleName`, `captureType`
(`"SCREENSHOT"` or `"VIDEO"`), `uploadDate`, `fileSize`, `resolution`,
`expireAt`, `screenshotUrl`, `largePreviewImage`, `smallPreviewImage`,
`videoUrl`, `titleImageUrl`, `conceptId`, and others.

Cursor-based pagination: use `nextCursorMark` from each response until it
becomes `"-1"`.

### 5.2 Get Capture Content Metadata

```
GET /api/gameMediaService/v2/c2s/content
    ?fields=title,description,broadcastDate,sceTitleName,countOfViewers,sceUserOnlineId,streamingPreviewImage,serviceType,channelId,sceTitleId,isSpoiler,transcodeStatus
    &ugcIds={ugcId}
```

Returns full metadata for one capture, same shape as items in 5.1.

### 5.3 Get Signed Download URLs

```
GET /api/gameMediaService/v2/c2s/ugc/{ugcId}/url
```

Returns time-limited signed CDN URLs for downloading capture assets:

```json
{
  "screenshotUrl": "https://...",
  "largePreviewImage": "https://...",
  "smallPreviewImage": "https://...",
  "downloadUrl": "https://...",
  "videoUrl": "https://..."
}
```

These URLs can be fetched directly (no auth needed) to download the binary asset.
The `screenshotUrl` is typically the highest-quality still.

---

## 6. GraphQL API

Base: `https://m.np.playstation.com/api/graphql/v1/op`

The PlayStation App uses Apollo persisted queries — the full GraphQL document
text is not sent; instead, a SHA-256 hash of the query is sent as an extension,
and the server looks up the operation by name + hash.

All GraphQL requests are `GET` requests with query parameters for
`operationName`, `variables`, and `extensions`.

Headers:
```
Authorization: Bearer {access_token}
Accept: application/json
Content-Type: application/json
apollographql-client-name: PlayStationApp-Android
apollographql-client-version: 26.4.0
x-psn-correlation-id: {uuid}
x-psn-request-id: {uuid}
x-psn-app-ver: PlayStationApp-Android/26.4.0
disable_query_whitelist: false
x-psn-store-locale-override: en-US
Accept-Language: en-US
Accept-Encoding: gzip
User-Agent: okhttp/4.12.0
```

### 6.1 Recently Played Titles (`metGetRecentPlayedTitles`)

SHA-256: `2fd023209ae806e5ed59c0dc061c1a49fcd51788226d549b1c8cb310be2da9ba`

Variables:
```json
{
  "accountId": "{accountId}",
  "count": 3,
  "categories": "ps4_game,ps5_native_game,pspc_game",
  "shouldFetchCodex": true
}
```

Response (data path: `data.recentPlayedTitlesRetrieve[]`):

```json
{
  "id": "...",
  "npTitleId": "...",
  "playTimeHours": 42,
  "storyProgress": 15,
  "hasHelpContent": true,
  "codexSummary": {"hasCodex": true, "hasNewEntries": false, "unlockedCodexCount": 23},
  "title": {
    "conceptId": "...",
    "name": "Game Name",
    "platform": "PS5",
    "media": [{"role": "MASTER", "type": "IMAGE", "url": "https://..."}]
  }
}
```

### 6.2 Hint Availability (`metGetHintAvailability`)

SHA-256: `71bf26729f2634f4d8cca32ff73aaf42b3b76ad1d2f63b490a809b66483ea5a7`

Variables:
```json
{
  "npCommId": "{npCommunicationId}",
  "trophyIds": ["0", "1", "2"]
}
```

Returns whether Game Help hint content exists for the specified trophy IDs.
Can return `data: null` with no errors (no hints available) or populated data.

### 6.3 Account / PS Stars (`metGetAccount`)

SHA-256: *embedded in app bundle, not verified in this project*

Variables: `{"accountId": "{accountId}"}`

Returns PS Stars loyalty account data: status level, points balance, collectible
scene info, and displayed collectibles. Confirmed working via probe. Provides the
collectible asset URLs needed for PS Stars display case rendering.

### 6.4 Collectible Display (`metGetCollectibleDisplay`)

SHA-256: *embedded in app bundle, not verified in this project*

Variables: `{"accountId": "{accountId}"}`

Returns PS Stars collectible display case layout and the collectible items
showcased with their media asset URLs. Confirmed working via probe.

### 6.5 Experience / Navigation (`metGetExperience`)

SHA-256: *embedded in app bundle*

Returns the navigation tree and experience config for the mobile client.
Confirmed working via probe.

### 6.6 Additional GraphQL Operations (in bundle, not all probed)

The APK bundle contains persisted query names for many more operations including
store wishlist (`metAddItemToStoreWishlist`, `metGetStoreWishlist`), category
browsing (`metGetCategoryGrid`, `metGetCategoryStrands`), product lookups
(`metGetConceptById`, `metGetProductById`), subscriptions
(`metSubscriptionBenefitsQuery`), and friends/social queries. These have not
all been verified in this project but are present in the shipped app.

---

## 7. Other Endpoints (Confirmed via Probe)

These endpoints were confirmed working during live probes but are not yet
integrated into the Rust library.

### 7.1 App Configuration

```
GET https://theia.dl.playstation.net/metropolis/config/top.json
```

Returns static app configuration (feature flags, build overrides). No auth required.

### 7.2 Registered Consoles (Cloud Assisted Navigation)

```
GET /api/cloudAssistedNavigation/v2/users/me/clients
Authorization: Bearer {access_token}
```

Returns the account's registered PS5/PS4 consoles and installed-title snapshots.

### 7.3 Feature Variants (Univex)

```
GET /api/univex/v3/platforms/mobile/variants
Authorization: Bearer {access_token}
```

Returns experiment/feature variant assignments for the mobile platform.

### 7.4 Basic Presence

```
GET /api/userProfile/v2/internal/users/{accountId}/basicPresences
Authorization: Bearer {access_token}
```

Returns online status, availability, last-online timestamp, and primary platform.

### 7.5 Appear Offline Setting

```
GET /api/userProfile/v1/internal/users/me/userSettings/appearOffline
Authorization: Bearer {access_token}
```

Returns a boolean indicating whether the account is set to appear offline.

### 7.6 Friends

```
GET /api/userProfile/v2/internal/users/{accountId}/friends
```

Returns a friend list with account IDs only (used as an index for follow-up
profile/presence lookups).

### 7.7 Multi-Profile Lookup

```
GET /api/userProfile/v1/internal/users/profiles?accountIds={id1},{id2}
```

Bulk profile lookup — same response shape as 4.1 but for multiple accounts.

### 7.8 Multi-Presence Lookup

```
GET /api/userProfile/v2/internal/users/basicPresences?accountIds={id1},{id2}
```

Bulk presence lookup for multiple accounts.

### 7.9 Friend Requests

```
GET /api/userProfile/v1/internal/users/me/friends/receivedRequests
```

Returns incoming friend request inbox.

### 7.10 Friend Relationship Summary

```
GET /api/userProfile/v1/internal/users/{accountId}/friends/{friendId}/summary
```

Returns relationship metadata (isFavorite, isBlocking, friendRelation, etc.).

### 7.11 Share Profile URL

```
GET /api/cpss/v1/share/profile/{accountId}
```

Returns public profile-sharing URLs and a pre-rendered share image URL.

### 7.12 Social Eligibility Check

```
GET /api/cpss/v1/eligibilityCheck/batch
```

Returns feature eligibility booleans for session management, party, profile
sharing, read receipts, emoji reactions, message deletion, etc.

### 7.13 Notifications

```
GET /api/userNotificationManager/v1/users/me/notifications
Authorization: Bearer {access_token}
```

Full notification inbox with state, templates, deep links, and title IDs.

```
GET /api/userNotificationManager/v1/users/me/notifications
    ?sinceUpdatedDateTime={ISO_8601_timestamp}
```

Incremental poll since a timestamp — returns only newer changes.

### 7.14 Push Notification (WebSocket)

**Bootstrap:**

```
GET https://mobile-pushcl.np.communication.playstation.net/np/serveraddr
    ?version=3.0
    &fields=keepAliveStatus
    &keepAliveStatusType=6
Authorization: Bearer {access_token}
```

Returns `{"fqdn": "...", "retryIntervalMin": ..., "retryIntervalMax": ...}`.

**WebSocket connection:**

```
wss://{fqdn}/np/pushNotification
```

Upgrade headers:
```
Sec-WebSocket-Protocol: np-pushpacket
Sec-WebSocket-Version: 13
X-PSN-PROTOCOL-VERSION: 3.0
Authorization: Bearer {access_token}
X-PSN-APP-TYPE: MOBILE_APP.PSAPP
X-PSN-APP-VER: 26.4.0
X-PSN-OS-VER: 16
X-PSN-KEEP-ALIVE-STATUS-TYPE: 6
```

After connection, the client can send control frames:
- `method: 1003` — change user status (e.g., set `status: "active"`)
- `method: 1007` — change keep-alive status type

### 7.15 Gaming Lounge Groups

```
GET /api/gamingLoungeGroups/v1/members/me/groups
    ?favoriteFilter=favorite
GET /api/gamingLoungeGroups/v1/members/me/groups
    ?favoriteFilter=notFavorite
```

Group chat list, split by favorite status.

```
GET /api/gamingLoungeGroups/v1/members/me/groups/{groupId}
```

Detail record for one group: name, icon, members, main thread ID, notification
settings.

```
GET /api/gamingLoungeGroups/v1/members/me/groups/{groupId}/threads/{threadId}/messages
```

Message timeline with chat messages, pagination cursors, and attachment URLs.
Add `?messageTypeFilter=1201` to get reactions/system messages only.

```
GET /api/gamingLoungeGroups/v1/members/me/groups/openPartySessions
```

Open party sessions exposed through the social layer.

```
GET /api/gamingLoungeGroups/v1/reactions/mobile-v1/definitions
```

Reaction vocabulary (emoji mappings for `heart`, `laugh`, `thumbup`, etc.).

### 7.16 Game Library

```
GET /api/gamelist/v2/users/{accountId}/titles
```
Full game library across PS4, PS5, PSPC categories. Add
`?categories=ps4_game,ps5_native_game` to filter to console only.

### 7.17 Entitlements

```
GET /api/entitlement/v2/users/me/internal/entitlements
GET /api/entitlement/v2/users/me/internal/entitlements?isActive=true
```

License/entitlement inventory for the account. `isActive=true` filters to
currently active licenses only.

### 7.18 Subscriptions

```
GET /api/subscriptions/v2/users/me/services/pssubscriptions
```

PS Plus and partner service subscription status, trial eligibility, renewal dates.

### 7.19 Explore Hub

```
GET /api/explore/v2/users/me/hub
```

Explore tab personalization: followed concepts, beta enrollments, story rails.

### 7.20 Party Session Invitations

```
GET /api/sessionManager/v2/users/me/partySessionsInvitations
```

Party invitation endpoint. May return an authorization error for some accounts.

### 7.21 Stickers

```
GET https://static-resource.np.community.playstation.net/sticker/presetList_US.json
```

Regional sticker catalog index with preset package IDs and manifest URLs.
Each manifest maps a sticker package to thumbnail, per-size image URLs, and
ZIP download URLs. No auth required.

### 7.22 WordPress Blog Posts

```
GET https://blog.playstation.com/wp-json/wp/v2/posts
```

Standard WordPress REST API for PlayStation Blog posts. Regional variants exist
(`blog.fr.playstation.com`, `blog.de.playstation.com`, etc.). No auth required.

---

## 8. Environment Variants

The production environment uses these base hosts:

| Service | Host (production) |
|---|---|
| Sony Account (auth) | `ca.account.sony.com` |
| DMS (device/account) | `dms.api.playstation.com` |
| Mobile API (REST) | `m.np.playstation.com` |
| Mobile API (GraphQL) | `m.np.playstation.com` |
| Push bootstrap | `mobile-pushcl.np.communication.playstation.net` |
| Theia (config) | `theia.dl.playstation.net` |
| Static resources | `static-resource.np.community.playstation.net` |

Non-production environments (`e1-np`, `mgmt`) substitute `np` in these hosts:
- `ca.e1-np.account.sony.com`
- `m.e1-np.playstation.com`
- `mobile-pushcl.e1-np.communication.playstation.net`
- etc.

The production env label `np` is the default used by this project.

---

## 9. Common Request Headers

### REST API (mobile headers)

```
Authorization: Bearer {access_token}
Accept-Language: en-US
Accept-Encoding: gzip
User-Agent: okhttp/4.12.0
If-Modified-Since: {RFC_2822_date_13min_ago}
x-psn-request-id: {uuid}
x-psn-app-ver: 26.4.0
x-psn-sampled: 0
x-psn-span-id: {8_byte_hex}
x-psn-trace-id: {8_byte_hex}
```

### GraphQL headers

Same as REST plus:
```
Accept: application/json
Content-Type: application/json
apollographql-client-name: PlayStationApp-Android
apollographql-client-version: 26.4.0
x-psn-correlation-id: {uuid}
x-psn-app-ver: PlayStationApp-Android/26.4.0
disable_query_whitelist: false
x-psn-store-locale-override: en-US
```
