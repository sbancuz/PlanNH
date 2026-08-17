package com.sbancuz.plannh.gui.node;

import java.util.List;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.HandlerInfo;

public interface NEIRecipeWidgetAccessor {

    void plannh$setUpdate(boolean update);

    HandlerInfo plannh$getHandlerInfo();

    void plannh$setInputs(List<PositionedStack> inputs);
}
