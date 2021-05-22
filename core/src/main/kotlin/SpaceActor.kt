import FirstScreen.Kind
import FirstScreen.Kind.BOMB
import FirstScreen.Kind.MISSILE
import FirstScreen.Kind.PLAYER
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import ktx.assets.Asset
import ktx.collections.GdxArray
import ktx.scene2d.actor
import ktx.scene2d.actors

class SpaceActor(
  private val texture: Asset<Texture>,
  val kind: Kind,
  private val missile: Asset<Texture>? = null,
  private val bomb: Asset<Texture>? = null,
) : Actor() {
  private val missiles: GdxArray<SpaceActor> = GdxArray()
  private val bombs: GdxArray<SpaceActor> = GdxArray()

  init {
    setBounds(x, y, texture.asset.width.toFloat(), texture.asset.height.toFloat())
  }

  override fun draw(batch: Batch?, parentAlpha: Float) {
    batch?.let {
      var oldColor = it.color
      it.color = this.color
      it.draw(
        texture.asset,
        x,
        y,
        width,
        height,
      )
      it.color = oldColor
    }
    super.draw(batch, parentAlpha)
  }

  fun dropBomb(initX: Float, initY: Float): SpaceActor? {
    if (bomb == null || kind == PLAYER) return null
    val newBomb: SpaceActor
    stage.actors {
      actor(SpaceActor(texture = bomb, kind = BOMB)) {
        setPosition(initX, initY)
        addAction(Actions.forever(Actions.moveBy(0f, -1f)))
      }.also { newBomb = it }
    }
    return newBomb.also { bombs.add(it) }
  }

  fun fireMissile(initX: Float, initY: Float): SpaceActor? {
    if (missile == null || kind != PLAYER) return null
    val newMissile: SpaceActor
    stage.actors {
      actor(SpaceActor(texture = missile, kind = MISSILE)) {
        setPosition(initX, initY)
        addAction(Actions.forever(Actions.moveBy(0f, 1f)))
      }.also { newMissile = it }
    }
    return newMissile.also { missiles.add(it) }
  }
}