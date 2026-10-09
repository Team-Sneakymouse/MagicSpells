<div align="center">
	<br>
	<img src="https://media.discordapp.net/attachments/335237931633606656/595352328341684427/Untitled.png" alt="MagicSpells Icon">
	<hr>
	<h2><i>Magic without writing Java</i></h2>
	<!--
            Build badge is commented out until it is fixed.
            <a href="https://travis-ci.org/TheComputerGeek2/MagicSpells"><img src="https://travis-ci.org/TheComputerGeek2/MagicSpells.svg?branch=main" alt="Build Status"></a>
        -->
	<a href="https://discord.magicspells.dev"><img src="https://img.shields.io/discord/335237931633606656?color=5562e9&logo=discord&logoColor=white" alt="Discord Server"></a>
	<a href="https://github.com/TheComputerGeek2/MagicSpells/releases"><img src="https://img.shields.io/github/downloads/TheComputerGeek2/MagicSpells/total.svg" alt="Github Releases"></a>
	<img src="https://img.shields.io/bstats/servers/892" alt="In MC servers">
</div>

[//]: # (These links are here for easier hyperlink referencing and less clutter in the actual text below.)
[Discord server]: https://discord.magicspells.dev
[showcase channel]: https://canary.discord.com/channels/335237931633606656/468537255925907466
[Releases]: https://github.com/TheComputerGeek2/MagicSpells/releases
[Wiki]: https://github.com/TheComputerGeek2/MagicSpells/wiki
[SpellRepo]: https://github.com/niblexis/ms-examples
[PAPIExp]: https://github.com/JasperLorelai/Expansion-MagicSpells
[Soundboard]: https://jasperlorelai.eu/soundboard/
[Nisovin]: https://nisovin.com/
[Bukkit]: https://dev.bukkit.org/projects/magicspells
[Spigot]: https://www.spigotmc.org/resources/magicspells.60847/
[PaperMC]: https://papermc.io/
[Modrinth]: https://modrinth.com/plugin/magicspells
[DiscordBadge]: https://img.shields.io/badge/Join%20our%20Discord-blue?style=for-the-badge&color=586ff2
[WelcomeChannel]: https://media.discordapp.net/attachments/423551934784339968/957725621503541288/unknown.png

MagicSpells is a [PaperMC] plugin which gives its users the ability to modify their Minecraft servers by configuring existing features without writing Java code. It provides you with the tools for playing with blocks of logic, bringing other plugins together, playing fantastic special effects, making your own new mechanics, and more.

Check out more examples of what this plugin can do in our [Discord server] (in the [showcase channel]).

---
## Resources 📝
* [Plugin Releases (Downloads)][Releases]
* [Community Wiki][Wiki]
* [Discord server]
* [Niblexis' Public Spell Repo][SpellRepo]
* [MagicSpells PAPI Expansion][PAPIExp]
* [JasperLorelai's Soundboard][Soundboard]

---
## Classical MagicSpells ⭐
This plugin was originally created by [Nisovin], and published on [Bukkit]. After some time TheComputerGeek2 took over the project and published it on [Spigot]. Since then we dropped support for Spigot and moved to [PaperMC], and the plugin was published on [Modrinth].

---
## Support 📞
If you need help with the plugin, our Discord server can provide you community support. You can also post suggestions there or find more resources that can be useful for you.

[![Join our Discord][DiscordBadge]][Discord server]

![Screenshot of the server][WelcomeChannel]

---
## Building 🧱
The move to Gradle has made building much easier. After cloning and navigating to the source's directory, you can simply run this command:
```
./gradlew build
```
Simple right?

---
## Maven publication 📦

Artifacts are published under **`io.github.team-sneakymouse`** (for example `io.github.team-sneakymouse:magicspells-core`). Java package names in source remain `com.nisovin.magicspells`.

Versions use `yyyy.MM.dd-<12-character Git hash>`, with the date taken from the commit timestamp in UTC (same pattern as SneakyPocketbase). Gradle derives this from `git HEAD` for local builds and CI, and expands the same value into each module’s `plugin.yml`. Each new commit gets its own version; rebuilding the same commit reuses it. Uncommitted edits keep the current HEAD version, so commit changes before publishing. Builds require a Git checkout.

Published modules and artifact ids:

| Gradle project | Maven artifact id | Plugin / archive name |
|----------------|-------------------|------------------------|
| `:core` | `magicspells-core` | `MagicSpells-<version>.jar` (shaded plugin); plain library JAR `magicspells-core-<version>.jar` |
| `:shop` | `magicspells-shop` | `MagicSpellsShop-<version>.jar` |
| `:factions` | `magicspells-factions` | `MagicSpellsFactions-<version>.jar` |
| `:memory` | `magicspells-memory` | `MagicSpellsMemory-<version>.jar` |
| `:teams` | `magicspells-teams` | `MagicSpellsTeams-<version>.jar` |
| `:towny` | `magicspells-towny` | `MagicSpellsTowny-<version>.jar` |
| `:nms:shared` | `magicspells-nms-shared` | (library JAR) |

`:nms:v26_2` is excluded (Paperweight dev bundle only).

**What is published:** plain library JARs, sources, Javadoc, and POMs — not the shaded plugin JAR from `./gradlew :core:shadowJar`. Published POMs only declare dependencies on `io.github.team-sneakymouse` artifacts and Kotlin; consumers add Paper and other deps themselves.

### Credentials and publish commands

For a repository using username/password authentication, put these properties in your user-level Gradle configuration (`~/.gradle/gradle.properties`), or in this repo’s gitignored `.gradle/gradle.properties` (see [`gradle.properties.example`](gradle.properties.example)):

```properties
sneakyrpUsername=YOUR_UPLOAD_USERNAME
sneakyrpPassword=YOUR_UPLOAD_TOKEN
```

Publishing targets `https://maven.sneakyrp.com/releases`. For Reposilite, `sneakyrpUsername` is the token name, and `sneakyrpPassword` is its generated secret. The build uses HTTP Basic authentication. Never commit the token or pass it on the command line.

Dry-run locally (no upload credentials required):

```bash
./gradlew publishToMavenLocal
```

Publish all configured modules to sneakyrp:

```bash
./gradlew publishAllPublicationsToSneakyrpRepository
```

Publish a single module:

```bash
./gradlew :core:publishMavenPublicationToSneakyrpRepository
```

### CI

GitHub Actions builds and publishes on every push to **`DvZ`**, using the version derived from the checked-out Git commit. The workflow runs `./gradlew build`, then fetches `MAVEN_USERNAME` and `MAVEN_PASSWORD` from `/Maven` in the Infisical `lords-of-minecraft` project, `prod` environment, via OIDC. Make `INFISICAL_IDENTITY_ID` and `INFISICAL_DOMAIN` available as GitHub Actions secrets for this repository, and ensure the identity's OIDC policy allows this repository's `DvZ` branch and access to `/Maven`.

### Using published artifacts

Add the sneakyrp releases repository (and Paper’s repo for `paper-api`):

```kotlin
repositories {
    maven("https://maven.sneakyrp.com/releases")
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.116-stable")
    compileOnly("io.github.team-sneakymouse:magicspells-core:<version>")
}
```

Older `4.0-Beta-*` coordinates may still resolve from Maven Central for historical dependents. New publishes go to sneakyrp only.
