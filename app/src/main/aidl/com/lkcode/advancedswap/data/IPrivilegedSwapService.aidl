package com.lkcode.advancedswap.data;

interface IPrivilegedSwapService {
    String probe();
    String runOperation(String operation, int sizeMb);
}

