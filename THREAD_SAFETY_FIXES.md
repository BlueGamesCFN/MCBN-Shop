# MCBN-Shop Thread Safety - Quick Fix Reference

## Priority 1: CRITICAL FIXES (Do First)

### 1.1 ShopManager - Replace HashMap with ConcurrentHashMap

**File**: `src/main/java/de/mcbn/shops/shop/ShopManager.java`

**Change Line 21**:
```java
// BEFORE
private final Map<BlockPosKey, Shop> shops = new HashMap<>();

// AFTER
private final Map<BlockPosKey, Shop> shops = new ConcurrentHashMap<>();
```

**Also fix line 40**:
```java
// BEFORE
return Collections.unmodifiableCollection(shops.values());

// AFTER
return new ArrayList<>(shops.values());
```

---

### 1.2 AuctionManager - Replace three HashMaps with ConcurrentHashMap

**File**: `src/main/java/de/mcbn/shops/auction/AuctionManager.java`

**Change Lines 29-31**:
```java
// BEFORE
private final Map<String, Auction> auctions = new LinkedHashMap<>();
private final Map<UUID, List<ItemStack>> pendingItems = new HashMap<>();
private final Map<UUID, Integer> pendingCurrency = new HashMap<>();

// AFTER
private final Map<String, Auction> auctions = new ConcurrentHashMap<>();
private final Map<UUID, List<ItemStack>> pendingItems = new ConcurrentHashMap<>();
private final Map<UUID, Integer> pendingCurrency = new ConcurrentHashMap<>();
```

**Fix allAuctions() method (line 60)**:
```java
// BEFORE
public Collection<Auction> allAuctions() {
    return Collections.unmodifiableCollection(auctions.values());
}

// AFTER
public Collection<Auction> allAuctions() {
    return new ArrayList<>(auctions.values());
}
```

**Fix saveAuctions() iterations - Add defensive copies**:
```java
// BEFORE (line 135)
for (Auction a : auctions.values()) {

// AFTER
List<Auction> snapshotAuctions = new ArrayList<>(auctions.values());
for (Auction a : snapshotAuctions) {
```

```java
// BEFORE (line 154)
for (Map.Entry<UUID, List<ItemStack>> e : pendingItems.entrySet()) {

// AFTER
Map<UUID, List<ItemStack>> snapshotPending = new HashMap<>(pendingItems);
for (Map.Entry<UUID, List<ItemStack>> e : snapshotPending.entrySet()) {
```

```java
// BEFORE (line 157)
for (Map.Entry<UUID, Integer> e : pendingCurrency.entrySet()) {

// AFTER
Map<UUID, Integer> snapshotCurrency = new HashMap<>(pendingCurrency);
for (Map.Entry<UUID, Integer> e : snapshotCurrency.entrySet()) {
```

---

### 1.3 KeeperManager - Replace HashMap with ConcurrentHashMap

**File**: `src/main/java/de/mcbn/shops/keeper/KeeperManager.java`

**Change Line 21**:
```java
// BEFORE
private final Map<UUID, ShopKeeper> keepers = new HashMap<>();

// AFTER
private final Map<UUID, ShopKeeper> keepers = new ConcurrentHashMap<>();
```

**Fix save() method (line 63)**:
```java
// BEFORE
for (ShopKeeper k : keepers.values()) {

// AFTER
List<ShopKeeper> snapshot = new ArrayList<>(keepers.values());
for (ShopKeeper k : snapshot) {
```

---

### 1.4 OrderManager - Replace HashMap with ConcurrentHashMap

**File**: `src/main/java/de/mcbn/shops/order/OrderManager.java`

**Change Line 17**:
```java
// BEFORE
private final Map<UUID, PurchaseOrder> orders = new HashMap<>();

// AFTER
private final Map<UUID, PurchaseOrder> orders = new ConcurrentHashMap<>();
```

**Fix save() method (line 59)**:
```java
// BEFORE
for (PurchaseOrder o : orders.values()) {

// AFTER
List<PurchaseOrder> snapshot = new ArrayList<>(orders.values());
for (PurchaseOrder o : snapshot) {
```

---

### 1.5 ScoreboardService - Replace HashMaps with ConcurrentHashMap

**File**: `src/main/java/de/mcbn/shops/shop/ScoreboardService.java`

**Change Lines 28-29**:
```java
// BEFORE
private final Map<UUID, Scoreboard> prevBoards = new HashMap<>();
private final Map<UUID, Scoreboard> ourBoards = new HashMap<>();

// AFTER
private final Map<UUID, Scoreboard> prevBoards = new ConcurrentHashMap<>();
private final Map<UUID, Scoreboard> ourBoards = new ConcurrentHashMap<>();
```

**Fix stop() method (line 48)**:
```java
// BEFORE
ourBoards.forEach((uuid, board) -> {
    Player p = Bukkit.getPlayer(uuid);
    if (p != null && prevBoards.containsKey(uuid)) {
        p.setScoreboard(prevBoards.get(uuid));
    }
});

// AFTER
List<UUID> uuids = new ArrayList<>(ourBoards.keySet());
for (UUID uuid : uuids) {
    Scoreboard board = ourBoards.get(uuid);
    Player p = Bukkit.getPlayer(uuid);
    if (p != null && prevBoards.containsKey(uuid)) {
        p.setScoreboard(prevBoards.get(uuid));
    }
}
```

---

### 1.6 Main.reloadEverything() - Add synchronization

**File**: `src/main/java/de/mcbn/shops/Main.java`

**Add import**:
```java
import java.util.concurrent.locks.ReentrantReadWriteLock;
```

**Add field to Main class**:
```java
private final ReentrantReadWriteLock reloadLock = new ReentrantReadWriteLock();
```

**Wrap reloadEverything() method**:
```java
public void reloadEverything() {
    reloadLock.writeLock().lock();
    try {
        reloadConfig();
        messages.reload();
        
        shopManager.saveShops();
        auctionManager.saveAuctions();
        keeperManager.save();
        orderManager.save();
        
        shopManager.loadShops();
        auctionManager.loadAuctions();
        keeperManager.load();
        orderManager.load();
        
        bossBarService.reloadFromConfig();
        displayService.reload();
        tutorialBroadcastService.reload();
        
        if (tabChatIntegration != null) {
            tabChatIntegration.unregister();
            tabChatIntegration.integrate();
        }
    } finally {
        reloadLock.writeLock().unlock();
    }
}
```

**Add read lock to manager access methods** (optional but recommended):
```java
public ShopManager shops() {
    reloadLock.readLock().lock();
    try {
        return shopManager;
    } finally {
        reloadLock.readLock().unlock();
    }
}
```

---

## Priority 2: HIGH PRIORITY FIXES (Do Second)

### 2.1 Make task ID fields volatile

**Files affected**:
- `BossBarService.java` line 23
- `DisplayService.java` line 21
- `ScoreboardService.java` line 27
- `ShopperTask.java` line 32

**Change pattern**:
```java
// BEFORE
private int taskId = -1;

// AFTER
private volatile int taskId = -1;
```

**Or use AtomicInteger** (better for concurrency):
```java
import java.util.concurrent.atomic.AtomicInteger;

// BEFORE
private int taskId = -1;

// AFTER
private final AtomicInteger taskId = new AtomicInteger(-1);

// Usage updates:
// OLD: if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
// NEW: if (taskId.get() != -1) Bukkit.getScheduler().cancelTask(taskId.get());
// OLD: taskId = newId;
// NEW: taskId.set(newId);
```

---

### 2.2 ShopKeeper - Synchronize linked ArrayList

**File**: `src/main/java/de/mcbn/shops/keeper/ShopKeeper.java`

**Change Line 16**:
```java
// BEFORE
private final List<BlockPosKey> linked = new ArrayList<>();

// AFTER (Option A - synchronized list)
private final List<BlockPosKey> linked = Collections.synchronizedList(new ArrayList<>());

// OR AFTER (Option B - CopyOnWriteArrayList, better for reading)
private final List<BlockPosKey> linked = new CopyOnWriteArrayList<>();
```

**Add import**:
```java
import java.util.CopyOnWriteArrayList;
```

---

### 2.3 ChatPromptService - Fix event handler race condition

**File**: `src/main/java/de/mcbn/shops/chat/ChatPromptService.java`

**Fix onAsyncChatPaper() method (lines 49-55)**:
```java
// BEFORE
@EventHandler(priority = EventPriority.HIGHEST)
public void onAsyncChatPaper(AsyncChatEvent event) {
    Player p = event.getPlayer();
    if (!active.containsKey(p.getUniqueId())) return;  // Check
    Component comp = event.message();
    String msg = PlainTextComponentSerializer.plainText().serialize(comp).trim();
    event.setCancelled(true);
    handle(p, msg);  // Act (but check was before)
}

// AFTER
@EventHandler(priority = EventPriority.HIGHEST)
public void onAsyncChatPaper(AsyncChatEvent event) {
    Player p = event.getPlayer();
    UUID uuid = p.getUniqueId();
    BiConsumer<Player, String> handler = active.get(uuid);
    if (handler == null) return;  // Safe check
    Component comp = event.message();
    String msg = PlainTextComponentSerializer.plainText().serialize(comp).trim();
    event.setCancelled(true);
    // Manually process since we already have the handler
    if (active.remove(uuid) != null) {
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(p, msg));
    }
}
```

**Same fix for onAsyncChatSpigot() (lines 59-65)**:
```java
// BEFORE
@EventHandler(priority = EventPriority.HIGHEST)
public void onAsyncChatSpigot(AsyncPlayerChatEvent event) {
    Player p = event.getPlayer();
    if (!active.containsKey(p.getUniqueId())) return;
    String msg = event.getMessage().trim();
    event.setCancelled(true);
    handle(p, msg);
}

// AFTER
@EventHandler(priority = EventPriority.HIGHEST)
public void onAsyncChatSpigot(AsyncPlayerChatEvent event) {
    Player p = event.getPlayer();
    UUID uuid = p.getUniqueId();
    BiConsumer<Player, String> handler = active.get(uuid);
    if (handler == null) return;
    String msg = event.getMessage().trim();
    event.setCancelled(true);
    if (active.remove(uuid) != null) {
        Bukkit.getScheduler().runTask(plugin, () -> handler.accept(p, msg));
    }
}
```

---

## Priority 3: MEDIUM PRIORITY FIXES (Do Third)

### 3.1 DisplayService - Add defensive copy

**File**: `src/main/java/de/mcbn/shops/util/DisplayService.java`

**Change Line 45**:
```java
// BEFORE
private void tick() {
    for (Shop shop : shopManager.all()) {

// AFTER
private void tick() {
    List<Shop> shops = new ArrayList<>(shopManager.all());
    for (Shop shop : shops) {
```

---

### 3.2 BossBarService - Add null check for lots

**File**: `src/main/java/de/mcbn/shops/util/BossBarService.java`

**Change Lines 63-65**:
```java
// BEFORE
for (Auction a : auctions) {
    for (AuctionLot lot : a.lots()) {

// AFTER
for (Auction a : auctions) {
    List<AuctionLot> lots = new ArrayList<>(a.lots());
    for (AuctionLot lot : lots) {
```

---

## Testing Checklist

After applying fixes, test these scenarios:

- [ ] Create/remove shops rapidly while DisplayService updates
- [ ] Create/cancel auctions while BossBarService broadcasts
- [ ] Run `/mcbnshops reload` while players are shopping
- [ ] Create shopkeepers while plugin is auto-saving
- [ ] Send rapid chat prompts to same player
- [ ] Run auto-save while iterating auctions
- [ ] Plugin startup with large number of saved shops/auctions
- [ ] Player joins/leaves during scoreboard update
- [ ] Multiple shops on same block (edge case)
- [ ] Rapid start/stop of services

---

## Code Diff Summary

| File | Changes | Risk |
|------|---------|------|
| ShopManager.java | HashMap→ConcurrentHashMap + defensive copies | LOW |
| AuctionManager.java | 3x HashMap→ConcurrentHashMap + defensive copies | LOW |
| KeeperManager.java | HashMap→ConcurrentHashMap + defensive copies | LOW |
| OrderManager.java | HashMap→ConcurrentHashMap + defensive copies | LOW |
| ScoreboardService.java | 2x HashMap→ConcurrentHashMap + defensive copy | LOW |
| ShopKeeper.java | ArrayList→CopyOnWriteArrayList | LOW |
| Main.java | Add ReentrantReadWriteLock | MEDIUM |
| BossBarService.java | Make taskId volatile | LOW |
| DisplayService.java | Make taskId volatile | LOW |
| ChatPromptService.java | Fix event handler logic | LOW |
| ShopperTask.java | Make taskId volatile | LOW |

---

## Notes

1. **ConcurrentHashMap vs synchronized**: ConcurrentHashMap is better for high-concurrency scenarios and uses segment-based locking instead of full synchronization.

2. **Defensive copies**: Creating snapshots (ArrayList copies) before iteration prevents ConcurrentModificationException while still allowing concurrent modifications.

3. **volatile vs AtomicInteger**: For simple int fields, `volatile` is sufficient. Use `AtomicInteger` for more complex atomic operations.

4. **ReentrantReadWriteLock**: Allows multiple readers but exclusive writer access, perfect for reload operations while other threads read.

5. **CopyOnWriteArrayList**: Better for scenarios with more reads than writes. Good for ShopKeeper.linked since it's mostly read-only.

---

## Time Estimate

- Priority 1 fixes: ~2-3 hours
- Priority 2 fixes: ~1 hour
- Priority 3 fixes: ~30 minutes
- Testing: ~2-3 hours

**Total: ~6-7 hours**

