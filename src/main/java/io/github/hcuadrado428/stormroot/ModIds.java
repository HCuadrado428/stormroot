package io.github.hcuadrado428.stormroot;

import net.minecraft.resources.Identifier;

/** Único sitio donde se construyen identificadores "stormroot:algo". */
public final class ModIds {
    private ModIds() {
    }

    public static Identifier id(String path) {
        //? if >=1.21 {
        return Identifier.fromNamespaceAndPath(Stormroot.MOD_ID, path);
        //?} else {
        /*return new Identifier(Stormroot.MOD_ID, path);
        *///?}
    }
}
