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
import java.util.Optional;
import java.util.UUID;

public record AreasDeltaPayload(
        RegistryKey<World> dimension,
        List<AreaOperation> operations
) implements CustomPayload {
    public static final Id<AreasDeltaPayload> ID = new Id<>(Identifier.of(Craneshot.MOD_ID, "areas_delta"));

    public static final PacketCodec<RegistryByteBuf, AreasDeltaPayload> CODEC = PacketCodec.of(
            AreasDeltaPayload::write,
            AreasDeltaPayload::read
    );

    private AreasDeltaPayload(RegistryByteBuf buf) {
        this(
                RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                readOperations(buf)
        );
    }

    private static AreasDeltaPayload read(RegistryByteBuf buf) {
        return new AreasDeltaPayload(buf);
    }

    private static List<AreaOperation> readOperations(RegistryByteBuf buf) {
        int count = buf.readVarInt();
        List<AreaOperation> ops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ops.add(AreaOperation.read(buf));
        }
        return ops;
    }

    private void write(RegistryByteBuf buf) {
        buf.writeIdentifier(dimension.getValue());
        buf.writeVarInt(operations.size());
        for (AreaOperation op : operations) {
            op.write(buf);
        }
    }

    @Override
    public Id<AreasDeltaPayload> getId() {
        return ID;
    }

    public enum OperationType {
        ADD, UPDATE, REMOVE
    }

    public record AreaOperation(
            OperationType type,
            UUID areaId,
            Optional<AreaInstanceDTO> areaData
    ) {
        public static AreaOperation read(RegistryByteBuf buf) {
            OperationType type = buf.readEnumConstant(OperationType.class);
            UUID areaId = buf.readUuid();
            Optional<AreaInstanceDTO> areaData;
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                areaData = Optional.of(AreaInstanceDTO.read(buf));
            } else {
                areaData = Optional.empty();
            }
            return new AreaOperation(type, areaId, areaData);
        }

        public void write(RegistryByteBuf buf) {
            buf.writeEnumConstant(type);
            buf.writeUuid(areaId);
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                areaData.ifPresent(dto -> dto.write(buf));
            }
        }
    }
}
