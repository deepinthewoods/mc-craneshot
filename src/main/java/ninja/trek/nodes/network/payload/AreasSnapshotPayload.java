package ninja.trek.nodes.network.payload;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import ninja.trek.Craneshot;
import ninja.trek.nodes.model.AreaInstanceDTO;

import java.util.ArrayList;
import java.util.List;

public record AreasSnapshotPayload(RegistryKey<World> dimension, List<AreaInstanceDTO> areas) implements CustomPayload {
    public static final Id<AreasSnapshotPayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "areas_snapshot"));

    public static final PacketCodec<RegistryByteBuf, AreasSnapshotPayload> CODEC = PacketCodec.of(
            AreasSnapshotPayload::write,
            AreasSnapshotPayload::read
    );

    private AreasSnapshotPayload(RegistryByteBuf buf) {
        this(
                RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                readAreas(buf)
        );
    }

    public AreasSnapshotPayload(RegistryKey<World> dimension, List<AreaInstanceDTO> areas) {
        this.dimension = dimension;
        this.areas = List.copyOf(areas);
    }

    private static AreasSnapshotPayload read(RegistryByteBuf buf) {
        return new AreasSnapshotPayload(buf);
    }

    private static List<AreaInstanceDTO> readAreas(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<AreaInstanceDTO> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(AreaInstanceDTO.read(buf));
        }
        return list;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(dimension.getValue());
        buf.writeVarInt(areas.size());
        for (AreaInstanceDTO dto : areas) {
            dto.write(buf);
        }
    }

    @Override
    public Id<AreasSnapshotPayload> getId() {
        return ID;
    }
}
