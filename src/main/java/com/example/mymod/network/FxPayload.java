package com.example.mymod.network;

import com.example.mymod.MyMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record FxPayload(int kind, Vec3 a, Vec3 b, float radius, float duration, int color, int seed,
		int variant, float extra, float extra2) implements CustomPacketPayload {
	public static final Type<FxPayload> TYPE = new Type<>(MyMod.id("fx"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FxPayload> CODEC =
			CustomPacketPayload.codec(FxPayload::write, FxPayload::read);

	public FxPayload(int kind, Vec3 a, Vec3 b, float radius, float duration, int color, int seed) {
		this(kind, a, b, radius, duration, color, seed, 0, 0f, 0f);
	}

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(kind);
		buf.writeDouble(a.x);
		buf.writeDouble(a.y);
		buf.writeDouble(a.z);
		buf.writeDouble(b.x);
		buf.writeDouble(b.y);
		buf.writeDouble(b.z);
		buf.writeFloat(radius);
		buf.writeFloat(duration);
		buf.writeInt(color);
		buf.writeInt(seed);
		buf.writeVarInt(variant);
		buf.writeFloat(extra);
		buf.writeFloat(extra2);
	}

	private static FxPayload read(RegistryFriendlyByteBuf buf) {
		int kind = buf.readVarInt();
		Vec3 a = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		Vec3 b = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		float radius = buf.readFloat();
		float duration = buf.readFloat();
		int color = buf.readInt();
		int seed = buf.readInt();
		int variant = buf.readVarInt();
		float extra = buf.readFloat();
		float extra2 = buf.readFloat();
		return new FxPayload(kind, a, b, radius, duration, color, seed, variant, extra, extra2);
	}

	@Override
	public Type<FxPayload> type() {
		return TYPE;
	}
}
