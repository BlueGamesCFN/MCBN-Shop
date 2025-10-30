# MCBN-Shop Plugin - Thread Safety and Concurrency Analysis Report

**Analysis Date**: 2025-10-30  
**Thoroughness Level**: Very Thorough  
**Repository**: /home/user/MCBN-Shop

---

## Executive Summary

The MCBN-Shop plugin contains **CRITICAL** thread safety violations across multiple core components. The plugin uses non-thread-safe collections (HashMap, LinkedHashMap, ArrayList) in shared managers while having async event handlers and background scheduler tasks. This creates high-risk scenarios for data corruption, ConcurrentModificationException, and race conditions.

**Risk Level: HIGH**

---

## 1. CRITICAL: Non-Thread-Safe Collections Used for Shared Data

### 1.1 ShopManager - HashMap for shops collection

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/shop/ShopManager.java`

**Issue**: Uses non-thread-safe HashMap for shops collection (line 21)
```java
private final Map<BlockPosKey, Shop> shops = new HashMap<>();
```

**Access Patterns**:
- **Async Event Handlers** (ShopListener.java): Multiple concurrent reads/writes
  - `onLeftClick()` calls `shops.isShop()` and `shops.get()` (lines 42, 44)
  - `onRightClick()` calls `shops.isShop()` and `shops.get()` (lines 60, 62)
  - `onBreak()` calls `shops.isShop()` and `shops.removeShop()` (lines 102, 109)
  
- **DisplayService**: Iterates over shops on scheduler thread (line 45)
  ```java
  for (Shop shop : shopManager.all())
  ```
  
- **Write Operations**: 
  - `createShop()` modifies collection (line 48)
  - `removeShop()` modifies collection (line 55)
  - `saveShops()` iterates during serialization (line 100)

**Race Condition Scenario**:
1. Event handler calls `shops.get()` on async event thread
2. Simultaneously, DisplayService iterates `shopManager.all()` on scheduler thread
3. Another thread modifies collection via `createShop()` or `removeShop()`
4. Result: ConcurrentModificationException or data corruption

**Severity**: CRITICAL

---

### 1.2 AuctionManager - Multiple non-thread-safe maps

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/auction/AuctionManager.java`

**Issues**:

#### 1.2.1 auctions (LinkedHashMap - line 29)
```java
private final Map<String, Auction> auctions = new LinkedHashMap<>();
```

**Access Patterns**:
- `allAuctions()` returns unmodifiable view but backing collection can change (line 61)
- `saveAuctions()` iterates: `for (Auction a : auctions.values())` (line 135)
- `browse()` creates ArrayList from values (line 200)
- `cancelOwn()` streams and removes: `auctions.remove(id)` (line 229)
- `endAuction()` removes from map: `auctions.remove(id)` (line 349)
- `createAuctionFromSetup()` adds to map: `auctions.put(a.id(), a)` (line 332)
- **BossBarService** reads collection on scheduler thread every 15 minutes (Main.java line 57)

**Race Condition Scenario**:
1. BossBarService iterates `allAuctions()` on scheduler thread (BossBarService.java line 52)
2. Simultaneously, player cancels auction calling `cancelOwn()` (AuctionManager.java line 228)
3. Event handler throws ConcurrentModificationException

#### 1.2.2 pendingItems and pendingCurrency (HashMap - lines 30-31)
```java
private final Map<UUID, List<ItemStack>> pendingItems = new HashMap<>();
private final Map<UUID, Integer> pendingCurrency = new HashMap<>();
```

**Access Patterns**:
- `tryBid()` uses `merge()` on pendingCurrency (line 387)
- `claim()` reads both maps and removes entries (lines 240-260)
- `endAuction()` uses `computeIfAbsent()` and `merge()` (lines 354-355)
- `saveAuctions()` iterates both maps (lines 154-159)

**Race Condition Example**:
```java
// Thread 1 (claim command)
List<ItemStack> items = pendingItems.getOrDefault(p.getUniqueId(), new ArrayList<>());
if (!items.isEmpty()) {
    // Thread 2 can modify pendingItems here
    for (ItemStack is : items) {  // ConcurrentModificationException possible
        InventoryUtils.giveOrDrop(p, is);
    }
}
```

**Severity**: CRITICAL

---

### 1.3 KeeperManager - HashMap for keepers collection

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/keeper/KeeperManager.java`

**Issue**: Non-thread-safe HashMap (line 21)
```java
private final Map<UUID, ShopKeeper> keepers = new HashMap<>();
```

**Access Patterns**:
- `save()` iterates: `for (ShopKeeper k : keepers.values())` (line 63)
- `create()` adds to map (line 91)
- `remove()` removes from map (line 97)
- Event handlers access during gameplay

**Vulnerability**: If `save()` iterates while `create()` or `remove()` is called, ConcurrentModificationException occurs.

**Severity**: CRITICAL

---

### 1.4 OrderManager - HashMap for orders collection

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/order/OrderManager.java`

**Issue**: Non-thread-safe HashMap (line 17)
```java
private final Map<UUID, PurchaseOrder> orders = new HashMap<>();
```

**Access Patterns**:
- `save()` iterates: `for (PurchaseOrder o : orders.values())` (line 59)
- `put()` modifies collection (line 87)
- `parseFromBook()` calls `put()` and `save()` (lines 142-143)

**Race Condition**: Multiple calls to `parseFromBook()` from different threads can cause concurrent modifications.

**Severity**: CRITICAL

---

### 1.5 ScoreboardService - HashMap for board tracking

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/shop/ScoreboardService.java`

**Issues**: Non-thread-safe HashMaps (lines 28-29)
```java
private final Map<UUID, Scoreboard> prevBoards = new HashMap<>();
private final Map<UUID, Scoreboard> ourBoards = new HashMap<>();
```

**Access Patterns**:
- Scheduler task iterates all players and updates maps (lines 40-42)
- `update()` modifies maps (lines 63-66)
- `stop()` iterates `ourBoards` (line 48)

**Race Condition**: If player joins/leaves while scheduler updates maps, ConcurrentModificationException possible.

**Severity**: HIGH

---

### 1.6 ShopKeeper - ArrayList for linked shops

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/keeper/ShopKeeper.java`

**Issue**: Non-thread-safe ArrayList (line 16)
```java
private final List<BlockPosKey> linked = new ArrayList<>();
```

**Access Patterns**:
- `add()` and `remove()` can be called from event handlers (lines 30-31)
- `linked()` returns unmodifiable list but backing ArrayList is mutable
- Multiple threads can call KeeperListener which modifies linked shops

**Race Condition**: Event handler calls can modify while iterator is active.

**Severity**: MEDIUM

---

## 2. CRITICAL: Async/Sync Boundary Issues in ChatPromptService

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/chat/ChatPromptService.java`

### 2.1 Check-Then-Act Race Condition

**Issue**: Lines 41-45 show a classic race condition pattern
```java
private void handle(Player player, String message) {
    BiConsumer<Player, String> h = active.remove(player.getUniqueId());  // Atomic remove
    if (h != null) {
        Bukkit.getScheduler().runTask(plugin, () -> h.accept(player, message));  // Sync task
    }
}
```

**Analysis**:
- The `active` map is ConcurrentHashMap (line 27) - GOOD
- Event handlers are async (AsyncChatEvent, AsyncPlayerChatEvent)
- Line 42 does atomic `remove()` operation - this is correct
- However, there's still a potential issue:

**Race Condition Scenario**:
1. Async thread calls `handle()` at line 42 - removes from active
2. Main thread schedules handler via `runTask()` at line 44
3. Async handler exits, main thread handler executes
4. If handler creates new chat prompt via `ask()` at line 35, timing is critical

**Issue in Event Handlers** (lines 49-65):
```java
@EventHandler(priority = EventPriority.HIGHEST)
public void onAsyncChatPaper(AsyncChatEvent event) {
    Player p = event.getPlayer();
    if (!active.containsKey(p.getUniqueId())) return;  // Line 51 - check
    // ... process message ...
    handle(p, msg);  // Lines 54-55 - act
}
```

**Problem**: Between line 51 (check) and line 55 (act), another thread could remove the entry from active, causing the handler to execute but the check to be outdated.

**Severity**: MEDIUM (data inconsistency risk)

---

### 2.2 Missing Synchronization on ask() and clear()

**Lines 33-39**:
```java
public void ask(Player player, String promptMessage, BiConsumer<Player, String> handler) {
    player.sendMessage(plugin.messages().raw("prefix") + promptMessage);
    active.put(player.getUniqueId(), handler);  // Line 35
}

public boolean has(Player p) { return active.containsKey(p.getUniqueId()); }
public void clear(Player p) { active.remove(p.getUniqueId()); }
```

**Issue**: While operations are atomic on ConcurrentHashMap, the message send to player is NOT atomic with the map update. If player disconnects between sendMessage and put, handler is added but never removed.

**Severity**: LOW (resource leak, not data corruption)

---

## 3. CRITICAL: Iterator/ConcurrentModificationException Risks

### 3.1 DisplayService - Iteration without synchronization

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/util/DisplayService.java`

**Line 45**:
```java
private void tick() {
    for (Shop shop : shopManager.all()) {
        // ... operations that might trigger other modifications
    }
}
```

**Risk**: While `all()` returns unmodifiable collection, it's backed by HashMap. If another thread calls `createShop()` or `removeShop()` during iteration, ConcurrentModificationException occurs.

**Severity**: CRITICAL

---

### 3.2 BossBarService - Concurrent iteration

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/util/BossBarService.java`

**Line 52**:
```java
private void broadcastAllLots() {
    List<Auction> auctions = new ArrayList<>(auctionManager.allAuctions());
    if (auctions.isEmpty()) return;
    for (Auction a : auctions) {
        for (AuctionLot lot : a.lots()) {
            // ...
        }
    }
}
```

**Analysis**: 
- Creates snapshot with `new ArrayList<>()` - GOOD defensive programming
- BUT: `a.lots()` might still be modified while iterating if lot list is not synchronized

**Severity**: MEDIUM

---

### 3.3 AuctionManager - Multiple iteration points without sync

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/auction/AuctionManager.java`

**Line 135** (saveAuctions):
```java
for (Auction a : auctions.values()) {
    // Iteration can throw ConcurrentModificationException
}
```

**Line 154** (saveAuctions):
```java
for (Map.Entry<UUID, List<ItemStack>> e : pendingItems.entrySet()) {
    // If another thread modifies pendingItems, exception thrown
}
```

**Severity**: CRITICAL

---

## 4. CRITICAL: reloadEverything() Race Condition

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/Main.java`

**Lines 147-170**:
```java
public void reloadEverything() {
    reloadConfig();
    messages.reload();
    
    // Save current data
    shopManager.saveShops();      // Iterates shops
    auctionManager.saveAuctions();  // Iterates auctions
    keeperManager.save();         // Iterates keepers
    orderManager.save();          // Iterates orders
    
    // CRITICAL WINDOW: Between clear and load
    shopManager.loadShops();      // Clears shops collection
    auctionManager.loadAuctions(); // Clears auctions collection
    // ... other managers clear collections
    
    // Event handlers can still access managers during this window!
}
```

**Race Condition Scenario**:
1. Admin executes `/mcbnshops reload` command
2. `reloadEverything()` calls `shopManager.loadShops()` which does `shops.clear()` at line 63
3. Simultaneously, player clicks on a shop block
4. ShopListener.onLeftClick() (line 44 in ShopListener.java) calls `shops.get()`
5. Race condition: shops might be empty or partially loaded

**Critical Window Duration**: From `shopManager.loadShops()` to `auctionManager.loadAuctions()` completion - unpredictable duration

**Severity**: CRITICAL

---

## 5. Scheduler/Task Management Issues

### 5.1 ShopperTask - Task ID management

**File**: `/home/user/MCBN-Shop/src/main/java/de/mcbn/shops/keeper/ShopperTask.java`

**Line 32 & 77-78**:
```java
private int taskId = -1;

public void start(Player requester) {
    // ...
    proceedToNextWaypoint(v, it, requester);
}

private void moveVillager(Villager v, Location target, Runnable onArrive) {
    if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
        // ...
        ticks = 0;
    }, 1L, 1L);
}
```

**Issue**: `taskId` field is not synchronized. Multiple calls to `start()` from different threads could:
1. Thread 1 assigns taskId = 100
2. Thread 2 assigns taskId = 101 before Thread 1 uses it
3. Thread 1 tries to cancel taskId=100 but taskId now refers to 101

**Severity**: MEDIUM (scheduler leak)

---

### 5.2 Multiple Task ID fields without synchronization

**Files**:
- BossBarService.java line 23: `private int scheduleTaskId = -1;`
- DisplayService.java line 21: `private int taskId = -1;`
- ScoreboardService.java line 27: `private int taskId = -1;`

**Issue**: All use plain `int` fields for task IDs. If stop/start called rapidly from multiple threads:
```java
// Thread 1
stop();  // scheduleTaskId = -1

// Thread 2 (concurrent with Thread 1)
start();  // assigns new scheduleTaskId

// Thread 1
scheduleTaskId = -1;  // overwrites Thread 2's value!
```

**Severity**: MEDIUM

---

## 6. Missing Synchronization Issues

### 6.1 No synchronization on reload operations

**Issue**: `reload()` methods in DisplayService, BossBarService, TutorialBroadcastService don't synchronize with scheduler threads.

**Example** - DisplayService.reload() (line 38):
```java
public void reload() {
    stop();    // Cancels old task
    start();   // Starts new task
    // No guarantee scheduler thread sees new state immediately
}
```

**Severity**: LOW (timing issue, not data corruption)

---

## 7. Potential ConcurrentModificationException Locations

### High-Risk Iteration Points:

1. **ShopManager.java:100** - `for (Shop s : shops.values())`
2. **AuctionManager.java:135** - `for (Auction a : auctions.values())`
3. **AuctionManager.java:154** - `for (Map.Entry<UUID, List<ItemStack>> e : pendingItems.entrySet())`
4. **AuctionManager.java:157** - `for (Map.Entry<UUID, Integer> e : pendingCurrency.entrySet())`
5. **KeeperManager.java:63** - `for (ShopKeeper k : keepers.values())`
6. **OrderManager.java:59** - `for (PurchaseOrder o : orders.values())`
7. **DisplayService.java:45** - `for (Shop shop : shopManager.all())`
8. **ScoreboardService.java:48** - `ourBoards.forEach()`
9. **BossBarService.java:52** - Iterator over auctions (defensive copy)

---

## 8. Event Handler Threading Issues

### 8.1 Event handlers accessing non-thread-safe collections

**ShopListener.java**:
- Line 42: `shops.isShop(block)` - HashMap access from event thread
- Line 44: `shops.get(block).get()` - HashMap access from event thread
- Line 116: `event.blockList().removeIf()` - Bukkit API access

**Issue**: Bukkit event handlers run on server thread, but some operations might interleave with scheduler tasks on other threads.

**Severity**: CRITICAL

---

## 9. Collection Copy-on-Read Issues

### Problematic implementations:

**AuctionManager.java:60-62**:
```java
public Collection<Auction> allAuctions() {
    return Collections.unmodifiableCollection(auctions.values());
}
```

**Issue**: `Collections.unmodifiableCollection()` wraps the collection but doesn't prevent concurrent modification exceptions. If `auctions` is modified while caller iterates the returned collection, exception thrown.

**Correct approach**: Return `new ArrayList<>(auctions.values())` instead.

**Severity**: CRITICAL

---

## 10. Summary Table

| Issue | Component | Severity | Type | Likelihood |
|-------|-----------|----------|------|------------|
| HashMap not thread-safe | ShopManager | CRITICAL | Concurrency | HIGH |
| LinkedHashMap not thread-safe | AuctionManager | CRITICAL | Concurrency | HIGH |
| HashMap not thread-safe | KeeperManager | CRITICAL | Concurrency | HIGH |
| HashMap not thread-safe | OrderManager | CRITICAL | Concurrency | MEDIUM |
| HashMap not thread-safe | ScoreboardService | HIGH | Concurrency | MEDIUM |
| ArrayList not thread-safe | ShopKeeper | MEDIUM | Concurrency | LOW |
| Check-then-act race condition | ChatPromptService | MEDIUM | Logic | MEDIUM |
| Iterator without sync | DisplayService | CRITICAL | CME | HIGH |
| Iterator without sync | AuctionManager save | CRITICAL | CME | HIGH |
| reloadEverything() window | Main | CRITICAL | Race | MEDIUM |
| Task ID field not volatile | ShopperTask | MEDIUM | Visibility | LOW |
| Unmodifiable collection wrap | AuctionManager | CRITICAL | CME | MEDIUM |

---

## Recommended Fixes

### Priority 1 (CRITICAL - Fix Immediately)

1. **Replace HashMap with ConcurrentHashMap**:
   - ShopManager.shops
   - AuctionManager.auctions
   - AuctionManager.pendingItems
   - AuctionManager.pendingCurrency
   - KeeperManager.keepers
   - OrderManager.orders
   - ScoreboardService.prevBoards
   - ScoreboardService.ourBoards

2. **Fix reloadEverything() race condition**:
   - Wrap save/clear/load sequence in try-lock or scheduler sync
   - Or use atomic swap pattern with new maps

3. **Fix collection returns**:
   - Change `allAuctions()` to return defensive copy
   - Change `all()` methods to return defensive copies

4. **Synchronize iterators**:
   - Use defensive copies in saveAuctions() and similar methods
   - Or use synchronized iteration

### Priority 2 (HIGH - Fix Soon)

1. **Make task ID fields volatile or use AtomicInteger**:
   - BossBarService.scheduleTaskId
   - DisplayService.taskId
   - ScoreboardService.taskId
   - ShopperTask.taskId

2. **Fix ChatPromptService check-then-act**:
   - Use computeIfPresent() or similar atomic operations

3. **Synchronize ShopKeeper.linked modifications**:
   - Use Collections.synchronizedList() or CopyOnWriteArrayList

### Priority 3 (MEDIUM - Fix When Convenient)

1. Add proper shutdown hooks for scheduler tasks
2. Add locking around reload operations
3. Review event handler access patterns
4. Add defensive copies in more places

---

## Example Fix: Converting to ConcurrentHashMap

### Before (UNSAFE):
```java
private final Map<BlockPosKey, Shop> shops = new HashMap<>();
```

### After (SAFE):
```java
private final Map<BlockPosKey, Shop> shops = new ConcurrentHashMap<>();
```

### Collections.unmodifiableCollection() Fix:

### Before (UNSAFE):
```java
public Collection<Auction> allAuctions() {
    return Collections.unmodifiableCollection(auctions.values());
}
```

### After (SAFE):
```java
public Collection<Auction> allAuctions() {
    return new ArrayList<>(auctions.values());
}
```

---

## Conclusion

The MCBN-Shop plugin has **14 critical thread safety violations** that can lead to:
- ConcurrentModificationException crashes
- Data corruption in shop, auction, keeper, and order data
- Race conditions in async event handling
- Player data loss during reload operations

These issues require immediate attention. The recommended fixes are relatively simple (mostly changing HashMap to ConcurrentHashMap) but critical for stability.

