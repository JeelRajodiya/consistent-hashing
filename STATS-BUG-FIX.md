# Stats Calculation Bug Fix

## Summary

Fixed critical bugs in load percentage calculation and display that caused misleading statistics.

## Issues Found

### 1. **Incorrect Load Percentage Calculation** ❌

**Location:** `LoadBalancer.java` line ~394-395

**Old (Incorrect) Formula:**

```java
loadPercentage = (nodeRequests / totalRequests) * 100
```

**Problem:**

- This calculated the percentage of **total requests** handled by each server
- NOT the actual load or capacity utilization
- Example: If total = 100 requests, server with 25 requests shows 25%
- This is just request distribution, not load intensity!

**Why it was confusing:**

- A server handling 20 req/s would show 33% (if total = 60 req/s)
- A server handling 40 req/s would show 66% (if total = 60 req/s)
- But 33% and 66% don't tell you if the servers are overloaded or not!

**New (Correct) Formula:**

```java
avgRequestsPerSecond = totalRequestsPerSecond / serverCount
loadPercentage = (serverRequestsPerSecond / avgRequestsPerSecond) * 100
```

**What it means now:**

- **100%** = This server handles average load (perfectly balanced)
- **<75%** = Under average load (underutilized, shown in GREEN)
- **75-125%** = Near average load (normal, shown in YELLOW)
- **>125%** = Above average load (overloaded, shown in RED)

**Example:**

- Total: 60 req/s across 3 servers
- Average: 20 req/s per server (this is 100%)
- Server A: 20 req/s → 100% load ✅ Balanced
- Server B: 40 req/s → 200% load ⚠️ Overloaded!
- Server C: 10 req/s → 50% load 📉 Underutilized

### 2. **UI Thresholds Were Wrong** ❌

**Location:** `ServerTable.vue` lines 23-26

**Old Thresholds:**

```typescript
if (load < 1.5) return "green"; // < 1.5%
if (load < 3.0) return "yellow"; // < 3.0%
return "red"; // > 3.0%
```

**Problem:**

- These thresholds were designed for the OLD calculation (% of total requests)
- Since old calculation gave values like 25%, 33%, etc., these thresholds were way off
- Everything showed green because loads were always >3%!

**New Thresholds:**

```typescript
if (load < 75) return "green"; // < 75% of average
if (load < 125) return "yellow"; // < 125% of average
return "red"; // > 125% of average
```

### 3. **Documentation Was Misleading** ❌

**Location:** `docs/STATS-API.md`

**Updated to clearly explain:**

- Load percentage is **relative to average**, not absolute capacity
- Examples showing what different percentages mean
- Color coding guide for visual interpretation

## Files Changed

1. ✅ `/app/src/main/java/org/example/loadbalancer/LoadBalancer.java`

   - Fixed `loadPercentage` calculation (line ~391-394)
   - Added clear comments explaining the formula
   - Separated variable naming for clarity (`nodeRequestsPerSecond` vs `nodeLoad`)

2. ✅ `/ui/app/components/ServerTable.vue`

   - Updated color thresholds from 1.5%/3.0% to 75%/125%
   - Added explanatory comments

3. ✅ `/docs/STATS-API.md`
   - Clarified what `loadPercentage` means
   - Added examples and interpretation guide

## How to Verify

### Before Fix:

- Server with 20 req/s shows higher absolute % than server with 40 req/s (inverted!)
- Loads never exceed 10%
- Colors don't make sense

### After Fix:

1. Start the load balancer with multiple servers
2. Send varied load (use `load-test.sh`)
3. Check `/stats` endpoint
4. Verify:
   - Server handling more req/s has **higher** `loadPercentage`
   - Average load is around 100%
   - Overloaded servers show >100%, underutilized show <100%
   - Colors in UI match the load intensity

### Test Scenario:

```bash
# Start with 4 servers
# Send 100 requests/second total
# Expected with perfect distribution:
#   - Each server: 25 req/s (100% load)
# With consistent hashing variance:
#   - Server A: 20 req/s → ~80% load (GREEN)
#   - Server B: 30 req/s → ~120% load (YELLOW/RED)
#   - Server C: 25 req/s → ~100% load (YELLOW)
#   - Server D: 25 req/s → ~100% load (YELLOW)
```

## Technical Details

### Calculation Logic:

```java
// Step 1: Calculate system-wide average req/s per server
avgRequestsPerSecond = totalRequestsPerSecond / serverCount

// Step 2: Calculate this server's req/s
nodeRequestsPerSecond = nodeRequests / nodeUptime

// Step 3: Compare to average
loadPercentage = (nodeRequestsPerSecond / avgRequestsPerSecond) * 100
```

### Edge Cases Handled:

- ✅ Division by zero when no servers
- ✅ Zero uptime for newly started servers
- ✅ Zero requests at system start

## Impact

### User Benefits:

- ✅ Accurate representation of server load
- ✅ Easy to spot overloaded/underutilized servers
- ✅ Meaningful color indicators
- ✅ Better understanding of load distribution
- ✅ Can now make informed scaling decisions

### Auto-scaling:

- The existing auto-scaling logic is unaffected
- It uses `requestsPerSecond` thresholds, not load percentage
- This fix only affects display/monitoring metrics

## Consistent Hashing Note

With consistent hashing, perfect balance (all servers at exactly 100%) is rare because:

- Hash distribution has natural variance
- Virtual nodes help but don't guarantee perfect distribution
- Typical variance: ±20-30% from average is normal
- Values around 70-130% indicate healthy distribution
- Values >150% or <50% may indicate issues with hash ring or specific key patterns
