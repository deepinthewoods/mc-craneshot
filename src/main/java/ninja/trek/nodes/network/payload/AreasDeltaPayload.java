package ninja.trek.nodes.network.payload;

import ninja.trek.Craneshot;
import ninja.trek.nodes.model.AreaInstanceDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;

public record AreasDeltaPayload(
        ResourceKey<Level> dimension,
        List<AreaOperation> operations
) implements CustomPacketPayload {
    public static final Type<AreasDeltaPayload> ID = new Type<>(Identifier.fromNamespaceAndPath(Craneshot.MOD_ID, "areas_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AreasDeltaPayload> CODEC = StreamCodec.ofMember(
            AreasDeltaPayload::write,
            AreasDeltaPayload::read
    );

    private AreasDeltaPayload(RegistryFriendlyByteBuf buf) {
        this(
                ResourceKey.create(Registries.DIMENSION, buf.readIdentifier()),
                readOperations(buf)
        );
    }

    private static AreasDeltaPayload read(RegistryFriendlyByteBuf buf) {
        return new AreasDeltaPayload(buf);
    }

    private static List<AreaOperation> readOperations(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<AreaOperation> ops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ops.add(AreaOperation.read(buf));
        }
        return ops;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeIdentifier(dimension.identifier());
        buf.writeVarInt(operations.size());
        for (AreaOperation op : operations) {
            op.write(buf);
        }
    }

    @Override
    public Type<AreasDeltaPayload> type() {
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
        public static AreaOperation read(RegistryFriendlyByteBuf buf) {
            OperationType type = buf.readEnum(OperationType.class);
            UUID areaId = buf.readUUID();
            Optional<AreaInstanceDTO> areaData;
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                areaData = Optional.of(AreaInstanceDTO.read(buf));
            } else {
                areaData = Optional.empty();
            }
            return new AreaOperation(type, areaId, areaData);
        }

        public void write(RegistryFriendlyByteBuf buf) {
            buf.writeEnum(type);
            buf.writeUUID(areaId);
            if (type == OperationType.ADD || type == OperationType.UPDATE) {
                areaData.ifPresent(dto -> dto.write(buf));
            }
        }
    }
}
