package com.example.mymod.network;

import com.example.mymod.MyMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record LaunchSkillPayload(
		int skill,
		float radius,
		float damage,
		float speed,
		boolean breakBlocks,
		boolean fire,
		boolean protectSelf,
		boolean gravity,
		boolean cinema
) implements CustomPacketPayload {
	public static final Type<LaunchSkillPayload> TYPE = new Type<>(MyMod.id("launch_skill"));

	public static final StreamCodec<RegistryFriendlyByteBuf, LaunchSkillPayload> CODEC =
			CustomPacketPayload.codec(LaunchSkillPayload::write, LaunchSkillPayload::read);

	private void write(RegistryFriendlyByteBuf buf) {
		buf.writeVarInt(skill);
		buf.writeFloat(radius);
		buf.writeFloat(damage);
		buf.writeFloat(speed);
		buf.writeByte((breakBlocks ? 1 : 0) | (fire ? 2 : 0) | (protectSelf ? 4 : 0) | (gravity ? 8 : 0)
				| (cinema ? 16 : 0));
	}

	private static LaunchSkillPayload read(RegistryFriendlyByteBuf buf) {
		int skill = buf.readVarInt();
		float radius = buf.readFloat();
		float damage = buf.readFloat();
		float speed = buf.readFloat();
		int flags = buf.readByte();
		return new LaunchSkillPayload(skill, radius, damage, speed,
				(flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0, (flags & 8) != 0, (flags & 16) != 0);
	}

	@Override
	public Type<LaunchSkillPayload> type() {
		return TYPE;
	}
}
