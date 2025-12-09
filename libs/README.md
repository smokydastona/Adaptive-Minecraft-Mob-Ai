# libs Directory

This folder is for optional JAR dependencies that aren't available in Maven repositories.

## MCA Reborn (Optional)

To enable villager dialogue features, download MCA Reborn and place it here:

1. **Download** MCA Reborn for Minecraft 1.20.1 from:
   - https://www.curseforge.com/minecraft/mc-mods/minecraft-comes-alive-reborn/files
   - Look for version compatible with Forge 1.20.1

2. **Place** the downloaded JAR file in this `libs/` folder
   - Example: `libs/MCA-1.20.1-7.5.21.jar`

3. **Build** the mod - Gradle will automatically detect and include it
   ```bash
   ./gradlew build
   ```

## Notes

- The mod works **without** MCA Reborn (mob AI features only)
- Adding MCA Reborn JAR here enables dialogue generation features
- The `libs/` folder is gitignored - JARs won't be committed to the repo
- Users need to download MCA Reborn separately if they want dialogue features
