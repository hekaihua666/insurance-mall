from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.modules.product.models import Product

# 「在售」口径只在这里定义一次：商城目录（Java 的 GET /api/v1/products）和顾问侧的两条查询都按它筛。
# 如果将来这个条件不再只有 status 一个字段（比如要加下架时间、渠道可见性），
# 就得收敛成数据库视图或者只留 Java 一个出口，否则两边会静默不一致。
ON_SALE_STATUS = "active"

class ProductRepository:

    def __init__(self, session: AsyncSession):
        self.session = session


    async def query_by_category(
        self,
        category: str | None = None,
    ) -> list[Product]:

        # 1.动态组装查询条件
        # 1.1.产品必须是上架产品
        conditions = [Product.status == ON_SALE_STATUS]
        # 1.1.分类条件
        if category:
            conditions.append(Product.category == category)

        # 2.执行查询
        result = await self.session.scalars(
            select(Product)
            .where(*conditions)
            .order_by(Product.id.asc())
        )

        # 3.返回结果
        return result.all()

    async def query_candidates(
        self,
        categories: list[str],
        premium_min: Decimal | None,
        limit_per_category: int,
    ) -> list[Product]:
        """按险种顺序返回上架候选产品，每个险种单独限量。"""
        limit = max(limit_per_category, 0)
        if limit == 0:
            return []

        candidates: list[Product] = []
        # 重复险种只查一次，保留调用方传入的顺序。
        for category in dict.fromkeys(categories):
            query = select(Product).where(Product.status == ON_SALE_STATUS, Product.category == category)
            if premium_min is not None:
                query = query.where(Product.min_premium < premium_min)

            # 同价按 ID 排序，未标价产品排在最后。
            result = await self.session.scalars(
                query.order_by(Product.min_premium.asc().nulls_last(), Product.id.asc()).limit(limit)
            )
            candidates.extend(result.all())
        return candidates