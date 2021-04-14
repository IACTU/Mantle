package slimeknights.mantle.client.book;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resources.IResourceManager;
import net.minecraft.util.EnumTypeAdapterFactory;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.resource.IResourceType;
import net.minecraftforge.resource.ISelectiveResourceReloadListener;
import slimeknights.mantle.client.book.action.StringActionProcessor;
import slimeknights.mantle.client.book.action.protocol.ProtocolGoToPage;
import slimeknights.mantle.client.book.data.BookData;
import slimeknights.mantle.client.book.data.content.ContentBlank;
import slimeknights.mantle.client.book.data.content.ContentBlockInteraction;
import slimeknights.mantle.client.book.data.content.ContentCrafting;
import slimeknights.mantle.client.book.data.content.ContentImage;
import slimeknights.mantle.client.book.data.content.ContentImageText;
import slimeknights.mantle.client.book.data.content.ContentSmelting;
import slimeknights.mantle.client.book.data.content.ContentSmithing;
import slimeknights.mantle.client.book.data.content.ContentStructure;
import slimeknights.mantle.client.book.data.content.ContentText;
import slimeknights.mantle.client.book.data.content.ContentTextImage;
import slimeknights.mantle.client.book.data.content.ContentTextLeftImage;
import slimeknights.mantle.client.book.data.content.ContentTextRightImage;
import slimeknights.mantle.client.book.data.content.PageContent;
import slimeknights.mantle.client.book.data.deserializer.HexStringDeserializer;
import slimeknights.mantle.client.book.repository.BookRepository;
import slimeknights.mantle.network.MantleNetwork;
import slimeknights.mantle.network.packet.UpdateSavedPagePacket;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.function.Predicate;

@OnlyIn(Dist.CLIENT)
public class BookLoader implements ISelectiveResourceReloadListener {

  /**
   * GSON object to be used for book loading purposes
   */
  public static final Gson GSON = new GsonBuilder()
    .disableHtmlEscaping()
    .registerTypeAdapter(int.class, new HexStringDeserializer())
    .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
    .registerTypeHierarchyAdapter(ITextComponent.class, new ITextComponent.Serializer())
    .registerTypeHierarchyAdapter(Style.class, new Style.Serializer())
    .registerTypeAdapterFactory(new EnumTypeAdapterFactory()).create();

  /**
   * Maps page content presets to names
   */
  private static final HashMap<String, Class<? extends PageContent>> typeToContentMap = new HashMap<>();

  /**
   * Internal registry of all books for the purposes of the reloader, maps books to name
   */
  private static final HashMap<String, BookData> books = new HashMap<>();

  public BookLoader() {
    // Register page types
    registerPageType(ContentBlank.ID, ContentBlank.class);
    registerPageType(ContentText.ID, ContentText.class);
    registerPageType(ContentImage.ID, ContentImage.class);
    registerPageType(ContentImageText.ID, ContentImageText.class);
    registerPageType(ContentTextImage.ID, ContentTextImage.class);
    registerPageType(ContentTextLeftImage.ID, ContentTextLeftImage.class);
    registerPageType(ContentTextRightImage.ID, ContentTextRightImage.class);
    registerPageType(ContentCrafting.ID, ContentCrafting.class);
    registerPageType(ContentSmelting.ID, ContentSmelting.class);
    registerPageType(ContentSmithing.ID, ContentSmithing.class);
    registerPageType(ContentBlockInteraction.ID, ContentBlockInteraction.class);
    registerPageType(ContentStructure.ID, ContentStructure.class);

    // Register action protocols
    StringActionProcessor.registerProtocol(new ProtocolGoToPage());
    StringActionProcessor.registerProtocol(new ProtocolGoToPage(true, ProtocolGoToPage.GO_TO_RTN));
  }

  /**
   * Registers a type of page prefabricate
   *
   * @param name  The name of the page type
   * @param clazz The PageContent class for this page type
   * @RecommendedInvoke init
   */
  public static void registerPageType(String name, Class<? extends PageContent> clazz) {
    if (typeToContentMap.containsKey(name)) {
      throw new IllegalArgumentException("Page type " + name + " already in use.");
    }

    typeToContentMap.put(name, clazz);
  }

  /**
   * Gets a type of page prefabricate by name
   *
   * @param name The name of the page type
   * @return The class of the page type, ContentError.class if page type not registered
   */
  @Nullable
  public static Class<? extends PageContent> getPageType(String name) {
    return typeToContentMap.get(name);
  }

  /**
   * Adds a book to the loader, and returns a reference object
   * Be warned that the returned BookData object is not immediately populated, and is instead populated when the resources are loaded/reloaded
   *
   * @param name         The name of the book, modid: will be automatically appended to the front of the name unless that is already added
   * @param repositories All the repositories the book will load the sections from
   * @return The book object, not immediately populated
   */
  public static BookData registerBook(String name, BookRepository... repositories) {
    return registerBook(name, true, true, repositories);
  }

  /**
   * Adds a book to the loader, and returns a reference object
   * Be warned that the returned BookData object is not immediately populated, and is instead populated when the resources are loaded/reloaded
   *
   * @param name               The name of the book, modid: will be automatically appended to the front of the name unless that is already added
   * @param appendIndex        Whether an index should be added to the front of the book using a BookTransformer
   * @param appendContentTable Whether a table of contents should be added to the front of each section using a BookTransformer
   * @param repositories       All the repositories the book will load the sections from
   * @return The book object, not immediately populated
   */
  public static BookData registerBook(String name, boolean appendIndex, boolean appendContentTable, BookRepository... repositories) {
    BookData info = new BookData(repositories);

    books.put(name.contains(":") ? name : ModLoadingContext.get().getActiveContainer().getNamespace() + ":" + name, info);

    if (appendIndex) {
      info.addTransformer(BookTransformer.indexTranformer());
    }
    if (appendContentTable) {
      info.addTransformer(BookTransformer.contentTableTransformer());
    }

    return info;
  }

  public static void updateSavedPage(@Nullable PlayerEntity player, ItemStack item, String page) {
    if (player == null) {
      return;
    }
    if (player.getHeldItem(Hand.MAIN_HAND).isEmpty()) {
      return;
    }

    BookHelper.writeSavedPageToBook(item, page);
    MantleNetwork.INSTANCE.network.sendToServer(new UpdateSavedPagePacket(page));
  }

  /**
   * Reloads all the books, called when the resource manager reloads, such as when the resource pack or the language is changed
   */
  @Override
  public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate) {
    books.forEach((s, bookData) -> bookData.reset());
  }
}
