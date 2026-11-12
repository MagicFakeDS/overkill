package com.example.mymod.network;

import com.example.mymod.MyMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

public record CinemaPayload(int story, Vec3 target, float radius, float prelude) implements CustomPacketPayload {
	public static final Type<CinemaPayload> TYPE = new Type<>(MyMod.id("cinema"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CinemaPayload> CODEC =
			CustomPacketPayload.codec(CinemaPayload::write, CinemaPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(story);
		buf.writeDouble(target.x);
		buf.writeDouble(target.y);
		buf.writeDouble(target.z);
		buf.writeFloat(radius);
		buf.writeFloat(prelude);
	}

	private static CinemaPayload read(RegistryFriendlyByteBuf buf) {
		int story = buf.readVarInt();
		Vec3 target = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
		return new CinemaPayload(story, target, buf.readFloat(), buf.readFloat());
	}

	@Override
	public Type<CinemaPayload> type() {
		return TYPE;
	}
}
