# Server Pack Locator

*Originally written by [cpw](https://github.com/cpw/serverpacklocator), ported over the years by [marchermans](https://github.com/marchermans/serverpacklocator), [baileyholl](https://github.com/baileyholl/serverpacklocator), and [gigabit101](https://github.com/gigabit101/serverpacklocator/)*

## About

Server Pack Locator is a module that allows you to easily keep the Forge Modpacks of Minecraft: Java Edition clients in sync with a server.  
This is achieved by having SPL grab a manifest from a defined server, downloading any mods it doesn't have, and then passing the mods in the manifest along to be loaded.  
  
This fork of SPL is designed to not require authentication. This works great for public servers & servers where you don't care about mods being available, but if you want authentication then check out [cpw](https://github.com/cpw/serverpacklocator)'s or [marchermans](https://github.com/marchermans/serverpacklocator)' versions.

## Setup

Example configs have been provided in the Docs/ directory of this repo.

### Server

1. Drop serverpacklocator.jar into the `mods/` folder of your Forge server.
2. Create a `servermods/` folder & put any mods you want SPL to handle in here  
3. Update the config `servermods/serverpacklocator.toml`

### Client

1. Drop serverpacklocator.jar into the `mods/` folder of your Forge instance.
2. Put the SPL config at `servermods/serverpacklocator.toml`, pointing to the server's domain.

## Notes

- You MUST use HTTPS with a valid TLS certificate. The cheapest (free) way to do this is setting up with Let's Encrypt & a domain from a Dynamic DNS provider.
- Mods put into `servermods/` will be loaded on the Client AND the Server. Don't put any client-only or server-only mods in there!
- If there are multiple versions of mods in a directory, Forge will only load the latest version. Additionally, SPL will only add the latest version to the manifest, so no need to worry about clearing older versions out!
