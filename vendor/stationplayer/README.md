# stationplayer
## Rust crate for interacting with the PlayStation App APIs

Based off of captured HTTP API logs, we can currently:

- Generate a sign-in URL with same permissions as the PS App
- Process NPSSO cookie into access token
- Read basic info about the current user
- Read basic trophy data about the current user

Roadmap:

- Extensive info about current user
- Reading granular info about trophies
- Viewing captures uploaded from gaming sessions
- Playtime API and general play info (what games were played etc.)
- (Optional) messaging API and allowing sending/receiving messages from friends
