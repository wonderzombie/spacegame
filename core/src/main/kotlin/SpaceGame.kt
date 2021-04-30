import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Texture
import ktx.app.KtxGame
import ktx.assets.assetDescriptor
import ktx.assets.loadAsset

/** [com.badlogic.gdx.ApplicationListener] implementation shared by all platforms.  */
class SpaceGame : KtxGame<FirstScreen>() {
  private val assetManager: AssetManager = AssetManager()

  override fun create() {
    val missile = assetManager.loadAsset(assetDescriptor<Texture>("Missile.png"))
    val bomb = assetManager.loadAsset(assetDescriptor<Texture>("Bomb.png"))
    val enemyShip = assetManager.loadAsset(assetDescriptor<Texture>("EnemyShip01.png"))
    val playerShip = assetManager.loadAsset(assetDescriptor<Texture>("PlayerShip01.png"))

    addScreen(FirstScreen(missile, bomb, enemyShip, playerShip))
    setScreen(FirstScreen::class.java)
  }
}