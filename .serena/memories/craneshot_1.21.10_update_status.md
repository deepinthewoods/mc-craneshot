# Craneshot Mod - 1.21.10 Update Issues

## Current Status
Updated phase3.md with new networking API requirements for Minecraft 1.21.10.

## Networking API Changes (1.20.5+ / 1.21.10)

### Complete Rewrite to CustomPayload System
The Fabric Networking API has been fundamentally changed from PacketByteBuf-based to CustomPayload-based:

**Old API (pre-1.20.5):**
```java
// Sending
ServerPlayNetworking.send(player, identifier, PacketByteBufs.create().writeString("data"));

// Receiving  
ServerPlayNetworking.registerGlobalReceiver(identifier, (server, player, handler, buf, responseSender) -> {
    String data = buf.readString();
});
```

**New API (1.20.5+):**
```java
// Define payload as Java Record
public record MyPayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<MyPayload> ID = new CustomPayload.Id<>(Identifier.of("modid", "my_payload"));
    public static final PacketCodec<RegistryByteBuf, MyPayload> CODEC = 
        PacketCodec.tuple(PacketCodecs.STRING, MyPayload::data, MyPayload::new);
    
    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() { return ID; }
}

// Register in ModInitializer
PayloadTypeRegistry.playS2C().register(MyPayload.ID, MyPayload.CODEC);

// Sending
ServerPlayNetworking.send(player, new MyPayload("data"));

// Receiving
ServerPlayNetworking.registerGlobalReceiver(MyPayload.ID, (payload, context) -> {
    String data = payload.data();
    ServerPlayerEntity player = context.player();
});
```

### Key Differences:

1. **Payload Classes**: Must create Java Record classes implementing `CustomPayload`
2. **PacketCodec**: Use `PacketCodec<RegistryByteBuf, T>` instead of manual PacketByteBuf read/write
3. **Registration**: Must register payloads with `PayloadTypeRegistry` before use
4. **Sending**: Pass payload objects instead of (Identifier, PacketByteBuf)
5. **Receiving**: Handlers receive typed payload objects and context
6. **Thread Safety**: Server handlers run on server thread (safe), client handlers run on netty thread (must use execute())

### PacketCodec Utilities:
- Primitives: `PacketCodecs.INTEGER`, `.STRING`, `.DOUBLE`, `.BOOLEAN`
- UUID: `Uuids.PACKET_CODEC`
- Collections: `.collect(PacketCodecs.toList())`, `.collect(PacketCodecs::optional)`
- Tuples: `PacketCodec.tuple(codec1, getter1, codec2, getter2, ..., constructor)`
- Custom: `PacketCodec.of((buf, obj) -> {write...}, buf -> {read...})`

### Entity API Changes (1.21.9+)
- `Entity#getWorld()` → `Entity#getEntityWorld()`
- `Entity#getServer()` remains the same but ensure using correct method

### PersistentState API Changes (1.21.10)
- `PersistentState.Type` no longer exists
- Use `PersistentStateType` class instead (separate class, not nested)
- Constructor requires `BiFunction<NbtCompound, RegistryWrapper.WrapperLookup, T>`
- `writeNbt` signature now requires `RegistryWrapper.WrapperLookup` parameter

## References:
- Fabric Networking Docs: https://docs.fabricmc.net/develop/networking
- Fabric 1.20.5 Migration: https://fabricmc.net/2024/04/19/1205.html
- Fabric 1.21.9/1.21.10 Changes: https://fabricmc.net/2025/09/23/1219.html
- PacketCodec Tutorial: https://wiki.fabricmc.net/tutorial:codec
- Fabric Persistent State: https://wiki.fabricmc.net/tutorial:persistent_states
