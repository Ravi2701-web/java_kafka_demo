#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# ecom-realtime: Test Scenarios
# Run this AFTER all 3 services are started and inventory is seeded
# ─────────────────────────────────────────────────────────────────────────────

ORDER_SERVICE="http://localhost:8081"
INVENTORY_SERVICE="http://localhost:8082"

echo ""
echo "════════════════════════════════════════════"
echo " STEP 0: Seed Inventory"
echo "════════════════════════════════════════════"
curl -s -X POST $INVENTORY_SERVICE/api/inventory/seed
echo ""

echo ""
echo "════════════════════════════════════════════"
echo " SCENARIO 1: Happy Path Order"
echo " order-service → Feign→ inventory-service (exists=true)"
echo " → Kafka saga → CONFIRMED"
echo "════════════════════════════════════════════"
RESPONSE=$(curl -s -X POST $ORDER_SERVICE/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","productId":"PROD-001","quantity":2,"totalAmount":3998.00}')
echo "Response: $RESPONSE"
ORDER_ID=$(echo $RESPONSE | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
echo "Order ID: $ORDER_ID"
echo "Waiting 5 seconds for saga to complete..."
sleep 5
echo "Final order status:"
curl -s $ORDER_SERVICE/api/orders/$ORDER_ID
echo ""

echo ""
echo "════════════════════════════════════════════"
echo " SCENARIO 2: Fake Product (NEW FEATURE)"
echo " order-service → Feign → inventory-service (exists=false)"
echo " → 404 returned IMMEDIATELY, no Kafka event fired"
echo "════════════════════════════════════════════"
curl -s -X POST $ORDER_SERVICE/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-BAD","productId":"FAKE-PRODUCT-999","quantity":1,"totalAmount":99.00}'
echo ""
echo "(Should get: Product not found: FAKE-PRODUCT-999)"

echo ""
echo "════════════════════════════════════════════"
echo " SCENARIO 3: Out of Stock (Failure Path)"
echo " product EXISTS (Feign returns exists=true)"
echo " but inventory=0 → Kafka saga → FAILED"
echo "════════════════════════════════════════════"
echo "Setting AirPods stock to 0..."
curl -s -X PUT "$INVENTORY_SERVICE/api/inventory/PROD-003/stock?qty=0"
echo ""
RESPONSE3=$(curl -s -X POST $ORDER_SERVICE/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-003","productId":"PROD-003","quantity":1,"totalAmount":249.00}')
ORDER_ID3=$(echo $RESPONSE3 | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)
echo "Order ID: $ORDER_ID3"
sleep 3
echo "Final status (should be FAILED - out of stock):"
curl -s $ORDER_SERVICE/api/orders/$ORDER_ID3
echo ""

echo ""
echo "════════════════════════════════════════════"
echo " SCENARIO 4: Place 5 rapid orders"
echo "════════════════════════════════════════════"
for i in {1..5}; do
  curl -s -X POST $ORDER_SERVICE/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"customerId\":\"CUST-10$i\",\"productId\":\"PROD-002\",\"quantity\":1,\"totalAmount\":999.00}" \
    | grep -o '"orderId":"[^"]*"'
done

sleep 5
echo ""
echo "════════════════════════════════════════════"
echo " CHECK: Remaining inventory"
echo "════════════════════════════════════════════"
curl -s $INVENTORY_SERVICE/api/inventory
echo ""

echo ""
echo "════════════════════════════════════════════"
echo " DONE — Now check:"
echo "  Kafka UI:   http://localhost:8090"
echo "  Grafana:    http://localhost:3000 (admin/admin)"
echo "  Prometheus: http://localhost:9090"
echo "════════════════════════════════════════════"
