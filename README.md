![ArchGPT Logo.](https://i.ibb.co/zn8fx1Y/ARCHGPT-banner.png)
# ArchGPT - Conversational AI in Minecraft!

**Downloads:** Spigot | Modrinth<br>
**Discord:** https://discord.gg/emDFbsKNV4<br>
**Donations:** https://ko-fi.com/xidentified

## About
Hey, thanks for checking this out! ArchGPT aims to add life-like conversational capabilities to your server's NPCs.
The plugin utilizes the OpenAI API to generate relevant responses for your players, and LibreTranslate to translate
responses if needed.

### Features
- Set a 'default prompt' with base information to feed to all NPCs
- Set individual prompts for each NPC through config.yml or command
- Report inaccurate or inappropriate responses by clicking message
- NPCs are contextually aware, see this page for more info
- Context includes notable locations in the server for them to reference
- Parse PlaceholderAPI placeholders in your prompts
- 'Loading' bubble over NPC head when they type
- Chat colors supporting MiniMessage syntax
- Prompts new players to interact with NPCs to begin conversation
- Translate plugin messages and API responses to player locale
- Adjustable NPC response length
- Optionally split long reponses into seperate chats
- Broadcast a message from any NPC server-wide for events, etc
- End conversations by typing 'cancel' or whatever word you want
- Conversations end by exiting radius, or changing worlds
- NPCs remember their conversations with individual players
- Reset one NPC's memory, or all of them if needed
- SQLite and MySQL storage

### Compatibility
The plugin has been tested on Paper and Spigot 1.20.2. Requires Citizens.
