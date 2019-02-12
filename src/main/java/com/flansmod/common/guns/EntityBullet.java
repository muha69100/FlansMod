package com.flansmod.common.guns;

import java.util.List;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.flansmod.client.FlansModClient;
import com.flansmod.client.FlansModResourceHandler;
import com.flansmod.client.debug.EntityDebugVector;
import com.flansmod.common.FlansMod;
import com.flansmod.common.FlansModExplosion;
import com.flansmod.common.driveables.EntityPlane;
import com.flansmod.common.driveables.EntityVehicle;
import com.flansmod.common.driveables.mechas.EntityMecha;
import com.flansmod.common.guns.raytracing.FlansModRaytracer;
import com.flansmod.common.guns.raytracing.FlansModRaytracer.BulletHit;
import com.flansmod.common.network.PacketFlak;
import com.flansmod.common.types.InfoType;
import com.flansmod.common.vector.Vector3f;

public class EntityBullet extends EntityShootable
//implements IEntityAdditionalSpawnData
{
	private static int bulletLife = 600; // Kill bullets after 30 seconds
	public int ticksInAir;
	
	private FiredShot shot;
	/**
	 * For homing missiles
	 */
	public Entity lockedOnTo;
	
	public float currentPenetratingPower;
	
	private float yOffset;
	
	@SideOnly(Side.CLIENT)
	private boolean playedFlybySound;
	

	public EntityBullet(World world, FiredShot shot, Vec3d origin, Vec3d direction)
	{
		super(world);
		ticksInAir = 0;
		setSize(0.5F, 0.5F);
		this.shot = shot;
		
		setPosition(origin.x, origin.y, origin.z);
		motionX = direction.x;
		motionY = direction.y;
		motionZ = direction.z;
		setArrowHeading(motionX, motionY, motionZ, shot.getFireableGun().getGunSpread() * shot.getBulletType().bulletSpread, shot.getFireableGun().getBulletSpeed());
		
		currentPenetratingPower = shot.getBulletType().penetratingPower;
	}

	@Override
	protected void entityInit()
	{
	}
	
	@Override
	public AxisAlignedBB getCollisionBoundingBox()
	{
		return getEntityBoundingBox();
	}
	
	//@Override
	//public AxisAlignedBB getCollisionBox(Entity entity) 
	//{
	//	return null;
	//}
	
	public void setArrowHeading(double d, double d1, double d2, float spread, float speed)
	{
		spread /= 5F;
		float f2 = MathHelper.sqrt(d * d + d1 * d1 + d2 * d2);
		d /= f2;
		d1 /= f2;
		d2 /= f2;
		d *= speed;
		d1 *= speed;
		d2 *= speed;
		d += rand.nextGaussian() * 0.005D * spread * speed;
		d1 += rand.nextGaussian() * 0.005D * spread * speed;
		d2 += rand.nextGaussian() * 0.005D * spread * speed;
		motionX = d;
		motionY = d1;
		motionZ = d2;
		float f3 = MathHelper.sqrt(d * d + d2 * d2);
		prevRotationYaw = rotationYaw = (float)((Math.atan2(d, d2) * 180D) / 3.1415927410125732D);
		prevRotationPitch = rotationPitch = (float)((Math.atan2(d1, f3) * 180D) / 3.1415927410125732D);
		
		getLockOnTarget();
	}
	
	/**
	 * Find the entity nearest to the missile's trajectory, anglewise
	 */
	private void getLockOnTarget()
	{
		BulletType type = shot.getBulletType();
		
		if(type.lockOnToPlanes || type.lockOnToVehicles || type.lockOnToMechas || type.lockOnToLivings
				|| type.lockOnToPlayers)
		{
			Vector3f motionVec = new Vector3f(motionX, motionY, motionZ);
			Entity closestEntity = null;
			float closestAngle = type.maxLockOnAngle * 3.14159265F / 180F;
			
			for(Object obj : world.loadedEntityList)
			{
				Entity entity = (Entity)obj;
				if((type.lockOnToMechas && entity instanceof EntityMecha)
						|| (type.lockOnToVehicles && entity instanceof EntityVehicle)
						|| (type.lockOnToPlanes && entity instanceof EntityPlane)
						|| (type.lockOnToPlayers && entity instanceof EntityPlayer)
						|| (type.lockOnToLivings && entity instanceof EntityLivingBase))
				{
					Vector3f relPosVec = new Vector3f(entity.posX - posX, entity.posY - posY, entity.posZ - posZ);
					float angle = Math.abs(Vector3f.angle(motionVec, relPosVec));
					if(angle < closestAngle)
					{
						closestEntity = entity;
						closestAngle = angle;
					}
				}
			}
			
			if(closestEntity != null)
				lockedOnTo = closestEntity;
		}
	}
	
	@Override
	public void setVelocity(double d, double d1, double d2)
	{
		motionX = d;
		motionY = d1;
		motionZ = d2;
		if(prevRotationPitch == 0.0F && prevRotationYaw == 0.0F)
		{
			float f = MathHelper.sqrt(d * d + d2 * d2);
			prevRotationYaw = rotationYaw = (float)((Math.atan2(d, d2) * 180D) / 3.1415927410125732D);
			prevRotationPitch = rotationPitch = (float)((Math.atan2(d1, f) * 180D) / 3.1415927410125732D);
			setLocationAndAngles(posX, posY, posZ, rotationYaw, rotationPitch);
		}
	}
	
	@Override
	public void onUpdate()
	{
		super.onUpdate();
		
		BulletType type = shot.getBulletType();
		
		if(FlansMod.DEBUG && world.isRemote)
			world.spawnEntity(new EntityDebugVector(world, new Vector3f(posX, posY, posZ),
					new Vector3f(motionX, motionY, motionZ), 20));
		
		// Check the fuse to see if the bullet should explode
		ticksInAir++;
		if(ticksInAir > type.fuse && type.fuse > 0 && !isDead)
		{
			setDead();
		}
		
		if(ticksExisted > bulletLife)
		{
			setDead();
		}
		
		if(isDead)
			return;
		
		if(world.isRemote)
			onUpdateClient();
		
		Vector3f origin = new Vector3f(posX, posY, posZ);
		Vector3f motion = new Vector3f(motionX, motionY, motionZ);
		
		if(!world.isRemote)
		{
			Entity ignore = shot.getPlayerOptional().isPresent() ? shot.getPlayerOptional().get() : shot.getShooterOptional().orElse(null);
			Integer ping = 0;
			if (shot.getPlayerOptional().isPresent())
				ping = shot.getPlayerOptional().get().ping;
			
			List<BulletHit> hits = FlansModRaytracer.Raytrace(world, ignore, ticksInAir > 20, this, origin, motion, ping, 0f);
			
			// We hit something
			if(!hits.isEmpty())
			{
				for(BulletHit bulletHit : hits)
				{
					Vector3f hitPos = new Vector3f(origin.x + motion.x * bulletHit.intersectTime,
							origin.y + motion.y * bulletHit.intersectTime,
							origin.z + motion.z * bulletHit.intersectTime);

					currentPenetratingPower = ShotHandler.OnHit(world, hitPos, motion, shot, bulletHit, currentPenetratingPower);
					if (currentPenetratingPower <= 0f)
					{
						ShotHandler.onDetonate(world, shot, hitPos);
						setDead();
						break;
					}
				}
			}
		}
		
		// Movement dampening variables
		float drag = 0.99F;
		float gravity = 0.02F;
		// If the bullet is in water, spawn particles and increase the drag
		if(isInWater())
		{
			for(int i = 0; i < 4; i++)
			{
				float bubbleMotion = 0.25F;
				world.spawnParticle(EnumParticleTypes.WATER_BUBBLE, posX - motionX * bubbleMotion,
						posY - motionY * bubbleMotion, posZ - motionZ * bubbleMotion, motionX, motionY, motionZ);
			}
			drag = 0.8F;
		}
		motionX *= drag;
		motionY *= drag;
		motionZ *= drag;
		motionY -= gravity * type.fallSpeed;
		
		// Apply homing action
		if(lockedOnTo != null)
		{
			double dX = lockedOnTo.posX - posX;
			double dY = lockedOnTo.posY - posY;
			double dZ = lockedOnTo.posZ - posZ;
			double dXYZ = dX * dX + dY * dY + dZ * dZ;
			
			Vector3f relPosVec = new Vector3f(dX, dY, dZ);
			float angle = Math.abs(Vector3f.angle(motion, relPosVec));
			
			double lockOnPull = (angle) * type.lockOnForce;
			
			lockOnPull = lockOnPull * lockOnPull;
			
			motionX *= 0.95f;
			motionY *= 0.95f;
			motionZ *= 0.95f;
			
			motionX += lockOnPull * dX / dXYZ;
			motionY += lockOnPull * dY / dXYZ;
			motionZ += lockOnPull * dZ / dXYZ;
		}
		
		// Apply motion
		posX += motionX;
		posY += motionY;
		posZ += motionZ;
		setPosition(posX, posY, posZ);
		
		// Recalculate the angles from the new motion
		float motionXZ = MathHelper.sqrt(motionX * motionX + motionZ * motionZ);
		rotationYaw = (float)((Math.atan2(motionX, motionZ) * 180D) / 3.1415927410125732D);
		rotationPitch = (float)((Math.atan2(motionY, motionXZ) * 180D) / 3.1415927410125732D);
		// Reset the range of the angles
		for(; rotationPitch - prevRotationPitch < -180F; prevRotationPitch -= 360F)
		{
		}
		for(; rotationPitch - prevRotationPitch >= 180F; prevRotationPitch += 360F)
		{
		}
		for(; rotationYaw - prevRotationYaw < -180F; prevRotationYaw -= 360F)
		{
		}
		for(; rotationYaw - prevRotationYaw >= 180F; prevRotationYaw += 360F)
		{
		}
		rotationPitch = prevRotationPitch + (rotationPitch - prevRotationPitch) * 0.2F;
		rotationYaw = prevRotationYaw + (rotationYaw - prevRotationYaw) * 0.2F;
		
		// Particles
		if(type.trailParticles && world.isRemote && ticksInAir > 1)
		{
			spawnParticles();
		}
		
		// Temporary fire glitch fix
		if(world.isRemote)
			extinguish();
	}
	
	@SideOnly(Side.CLIENT)
	private void onUpdateClient()
	{
		if(getDistanceSq(Minecraft.getMinecraft().player) < 5 && !playedFlybySound)
		{
			playedFlybySound = true;
			FMLClientHandler.instance().getClient().getSoundHandler()
					.playSound(new PositionedSoundRecord(FlansModResourceHandler.getSoundEvent("bulletFlyby"), SoundCategory.HOSTILE, 10F,
							1.0F / (rand.nextFloat() * 0.4F + 0.8F), (float)posX, (float)posY, (float)posZ));
		}
	}
	
	@SideOnly(Side.CLIENT)
	private void spawnParticles()
	{
		double dX = (posX - prevPosX) / 10;
		double dY = (posY - prevPosY) / 10;
		double dZ = (posZ - prevPosZ) / 10;
		
		float spread = 0.1F;
		for(int i = 0; i < 10; i++)
		{
			Particle particle = FlansModClient.getParticle(shot.getBulletType().trailParticleType, world,
					prevPosX + dX * i + rand.nextGaussian() * spread, prevPosY + dY * i + rand.nextGaussian() * spread,
					prevPosZ + dZ * i + rand.nextGaussian() * spread);
			// TODO: [1.12] once again, render distance
			
			//if (particle != null && Minecraft.getMinecraft().gameSettings.fancyGraphics)
			//	particle.renderDistanceWeight = 100D;
			// world.spawnEntity(particle);
		}
	}
	
	//Not used
	/*
	@Deprecated
	public DamageSource getBulletDamage(boolean headshot)
	{
		if(owner instanceof EntityPlayer)
			return (new EntityDamageSourceGun(type.shortName, this, (EntityPlayer)owner, firedFrom, headshot))
					.setProjectile();
		else
			return (new EntityDamageSourceIndirect(type.shortName, this, owner)).setProjectile();
	}
	//Not used
	/*
	@Deprecated
	public static DamageSource getDamageSource(InfoType firedFrom, BulletType type, Entity owner, boolean headshot)
	{
		if (owner == null) {
			throw new NullPointerException("Owner cannot be null");
		}
		if(owner instanceof EntityPlayer)
		{
			return (new EntityDamageSourceGun(type.shortName, owner, (EntityPlayer)owner, firedFrom, headshot)).setProjectile();
		}
		
		return (new EntityDamageSourceIndirect(type.shortName, owner, owner)).setProjectile();
	}
	
	//Not used
	/*
	private boolean isPartOfOwner(Entity entity)
	{
		if(owner == null)
			return false;
		if(entity == owner || entity == owner.getControllingPassenger() || entity == owner.getRidingEntity())
			return true;
		if(owner instanceof EntityPlayer)
		{
			if(PlayerHandler.getPlayerData((EntityPlayer)owner,
					world.isRemote ? Side.CLIENT : Side.SERVER) == null)
				return false;
			EntityMG mg = PlayerHandler.getPlayerData((EntityPlayer)owner,
					world.isRemote ? Side.CLIENT : Side.SERVER).mountingGun;
			if(mg != null && mg == entity)
			{
				return true;
			}
		}
		return owner.getRidingEntity() instanceof EntitySeat && (((EntitySeat)owner.getRidingEntity()).driveable == null
				|| ((EntitySeat)owner.getRidingEntity()).driveable.isPartOfThis(entity));
	}
	*/
	
	@Override
	public void setDead()
	{
		if(isDead)
			return;
		super.setDead();
		
		ShotHandler.onDetonate(world, shot, new Vector3f(posX, posY, posZ));
	}
	
	@Deprecated
	public static void OnDetonate(World world, Vector3f detonatePos, EntityLivingBase owner, EntityBullet bullet, InfoType shotFrom, BulletType bulletType)
	{
		if(world.isRemote)
			return;
		if(bulletType.explosionRadius > 0)
		{
			new FlansModExplosion(world, bullet, owner, bulletType,
					detonatePos.x, detonatePos.y, detonatePos.z, bulletType.explosionRadius, bulletType.fireRadius > 0, bulletType.flak > 0, bulletType.explosionBreaksBlocks);
		}
		if(bulletType.fireRadius > 0)
		{
			for(float i = -bulletType.fireRadius; i < bulletType.fireRadius; i++)
			{
				for(float k = -bulletType.fireRadius; k < bulletType.fireRadius; k++)
				{
					for(int j = -1; j < 1; j++)
					{
						if(world.getBlockState(new BlockPos((int)(detonatePos.x + i), (int)(detonatePos.y + j), (int)(detonatePos.z + k))).getMaterial() == Material.AIR)
						{
							world.setBlockState(new BlockPos((int)(detonatePos.x + i), (int)(detonatePos.y + j), (int)(detonatePos.z + k)), Blocks.FIRE.getDefaultState(), 2);
						}
					}
				}
			}
		}
		// Send flak packet
		if(bulletType.flak > 0 && owner != null)
		{
			FlansMod.getPacketHandler().sendToAllAround(new PacketFlak(detonatePos.x, detonatePos.y, detonatePos.z, bulletType.flak, bulletType.flakParticles), detonatePos.x, detonatePos.y, detonatePos.z, 200, owner.dimension);
		}
		// Drop item on hitting if bullet requires it
		if(bulletType.dropItemOnHit != null)
		{
			String itemName = bulletType.dropItemOnHit;
			int damage = 0;
			if(itemName.contains("."))
			{
				damage = Integer.parseInt(itemName.split("\\.")[1]);
				itemName = itemName.split("\\.")[0];
			}
			ItemStack dropStack = InfoType.getRecipeElement(itemName, damage);
			
			if(dropStack != null && !dropStack.isEmpty())
			{
				EntityItem entityitem = new EntityItem(world, detonatePos.x, detonatePos.y, detonatePos.z, dropStack);
				entityitem.setDefaultPickupDelay();
				world.spawnEntity(entityitem);
			}
		}
	}
	
	@Override
	public void writeEntityToNBT(NBTTagCompound tag)
	{
		//bullets should not be saved, while it is theoretically possible it would create an huge amount of problems
		/*
		tag.setString("type", type.shortName);
		if(owner == null)
			tag.setString("owner", "null");
		else
			tag.setString("owner", owner.getName());
			*/
	}
	
	@Override
	public void readEntityFromNBT(NBTTagCompound tag)
	{
		//bullets should not be saved
		/*
		
		String typeString = tag.getString("type");
		String ownerName = tag.getString("owner");
		
		if(typeString != null)
			type = BulletType.getBullet(typeString);
		
		if(ownerName != null && !ownerName.equals("null"))
			owner = FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayerByUsername(ownerName);
			*/
	}
	
	public int getBrightnessForRender(float par1)
	{
		if(shot.getBulletType().hasLight)
		{
			return 15728880;
		}
		else
		{
			int i = MathHelper.floor(this.posX);
			int j = MathHelper.floor(this.posZ);
			
			if(!world.isAirBlock(new BlockPos(i, 0, j)))
			{
				double d0 = (getCollisionBoundingBox().maxY - getCollisionBoundingBox().minY) * 0.66D;
				int k = MathHelper.floor(this.posY - (double)this.yOffset + d0);
				return this.world.getLightFromNeighborsFor(EnumSkyBlock.SKY, new BlockPos(i, k, j));
			}
			else
			{
				return 0;
			}
		}
	}
	//There is no need to save/store this data
	/*
	@Override
	public void writeSpawnData(ByteBuf data)
	{
		data.writeDouble(motionX);
		data.writeDouble(motionY);
		data.writeDouble(motionZ);
		data.writeInt(lockedOnTo == null ? -1 : lockedOnTo.getEntityId());
		ByteBufUtils.writeUTF8String(data, type.shortName);
		if(owner == null)
			ByteBufUtils.writeUTF8String(data, "null");
		else
			ByteBufUtils.writeUTF8String(data, owner.getName());
	}
	
	@Override
	public void readSpawnData(ByteBuf data)
	{
		try
		{
			motionX = data.readDouble();
			motionY = data.readDouble();
			motionZ = data.readDouble();
			int lockedOnToID = data.readInt();
			if(lockedOnToID != -1)
				lockedOnTo = world.getEntityByID(lockedOnToID);
			type = BulletType.getBullet(ByteBufUtils.readUTF8String(data));
			penetratingPower = type.penetratingPower;
			String name = ByteBufUtils.readUTF8String(data);
			for(Object obj : world.loadedEntityList)
			{
				if(obj != null && ((Entity)obj).getName().equals(name))
				{
					owner = (EntityLivingBase)obj;
					break;
				}
			}
		}
		catch(Exception e)
		{
			FlansMod.log.error("Failed to read bullet owner from server.");
			super.setDead();
			FlansMod.log.throwing(e);
		}
	}
	*/	
	@Override
	public boolean isBurning()
	{
		
		return false;
	}
	
	@Override
	public boolean canBePushed()
	{
		return false;
	}
	
	public FiredShot getFiredShot()
	{
		return shot;
	}
}
