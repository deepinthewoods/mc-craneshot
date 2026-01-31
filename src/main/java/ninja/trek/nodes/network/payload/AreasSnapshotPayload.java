package ninja.trek.nodes.network.payload;

import ninja.trek.Craneshot;
import ninja.trek.nodes.model.AreaInstanceDTO;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public record AreasSnapshotPayload(ResourceKey<Level> dimension, List<AreaInstanceDTO> areas) implements CustomPacketPayload {
    public static final Type<AreasSnapshotPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "areas_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AreasSnapshotPayload> CODEC = StreamCodec.ofMember(
            AreasSnapshotPayload::write,
            AreasSnapshotPayload::read
    );

    private AreasSnapshotPayload(RegistryFriendlyByteBuf buf) {
        this(
                ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                readAreas(buf)
        );
    }

    public AreasSnapshotPayload(ResourceKey<Level> dimension, List<AreaInstanceDTO> areas) {
        this.dimension = dimension;
        this.areas = List.copyOf(areas);
    }

    private static AreasSnapshotPayload read(RegistryFriendlyByteBuf buf) {
        return new AreasSnapshotPayload(buf);
    }

    private static List<AreaInstanceDTO> readAreas(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<AreaInstanceDTO> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(AreaInstanceDTO.read(buf));
        }
        return list;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(dimension.identifier());
        buf.writeVarInt(areas.size());
        for (AreaInstanceDTO dto : areas) {
            dto.write(buf);
        }
    }

    @Override
    public Type<AreasSnapshotPayload> type() {
        return ID;
    }
}
