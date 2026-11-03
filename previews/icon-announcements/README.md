# Icon and announcements preview

Replace the existing Hardcore Reset JAR with `hardcorereset-1.0.3-preview.jar`.
Requirements remain Minecraft 26.3, Fabric API 0.165.0+26.3, Fabric Loader 0.20.1+,
and Java 25. Restart once after replacing the JAR; keep world/player files.

Changes:
- Added the supplied image as the mod icon and README image.
- Mod countdown, reset, and new-run announcements use large on-screen titles.
- The original dying player's in-game username stays in the subtitle throughout
  the countdown and reset. Other players' deaths do not replace that name.
- Vanilla Minecraft death messages are unchanged; the mod's announcements no
  longer write to chat.

Build and 14 controller tests passed. Visual in-game testing is left to the user.
Image source: https://i.redd.it/keto58lvfdfh1.png
