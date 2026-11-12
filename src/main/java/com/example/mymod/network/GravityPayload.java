package com.example.mymod.network;

import com.example.mymod.MyMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record GravityPayload(boolean enabled) implements CustomPacketPayload {
	public static final Type<GravityPayload> TYPE = new Type<>(MyMod.id("gravity"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GravityPayload> CODEC =
			CustomPacketPayload.codec((p, buf) -> buf.writeBoolean(p.enabled()), buf -> new GravityPayload(buf.readBoolean()));

	@Override
	public Type<GravityPayload> type() {
		return TYPE;
	}
}
