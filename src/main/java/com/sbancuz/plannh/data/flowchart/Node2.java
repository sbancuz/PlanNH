package com.sbancuz.plannh.data.flowchart;

import java.util.UUID;

import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.Recipe;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Node2 extends GraphData {

    private final Recipe.RecipeId recipeId;
    // needed for coloring
    private final String machineName;

    public Node2(IRecipeHandler handler, int recipeIndex) {
        super(UUID.randomUUID());

        /*
         * JsonObject obj = new JsonObject();
         * try {
         * final Minecraft mc = Minecraft.getMinecraft();
         * final String worldName = NEIClientConfig.getWorldPath();
         * final File saveFile = new File(mc.mcDataDir, "saves/NEI/" + worldName + "/plannh/nodeTest.json");
         * obj = Serializer.GSON
         * .fromJson(Files.readString(saveFile.toPath(), StandardCharsets.UTF_8), JsonObject.class);
         * } catch (final Exception ignored) {}
         * recipeId = Recipe.RecipeId.of(obj);
         */

        recipeId = Recipe.RecipeId.of(handler, recipeIndex);
        machineName = handler.getRecipeName()
            .trim();
        header = machineName;
    }

    @Override
    public String getType() {
        return "node";
    }
}
