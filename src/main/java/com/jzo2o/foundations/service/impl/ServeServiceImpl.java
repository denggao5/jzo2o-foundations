package com.jzo2o.foundations.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jzo2o.common.expcetions.CommonException;
import com.jzo2o.common.expcetions.ForbiddenOperationException;
import com.jzo2o.common.model.PageResult;
import com.jzo2o.foundations.enums.FoundationStatusEnum;
import com.jzo2o.foundations.mapper.RegionMapper;
import com.jzo2o.foundations.mapper.ServeItemMapper;
import com.jzo2o.foundations.mapper.ServeMapper;
import com.jzo2o.foundations.model.domain.Region;
import com.jzo2o.foundations.model.domain.Serve;
import com.jzo2o.foundations.model.domain.ServeItem;
import com.jzo2o.foundations.model.dto.request.ServePageQueryReqDTO;
import com.jzo2o.foundations.model.dto.request.ServeUpsertReqDTO;
import com.jzo2o.foundations.model.dto.response.ServeResDTO;
import com.jzo2o.foundations.service.IServeService;
import com.jzo2o.mysql.utils.PageHelperUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServeServiceImpl extends ServiceImpl<ServeMapper, Serve> implements IServeService {

    @Resource
    private ServeItemMapper serveItemMapper;

    @Resource
    private RegionMapper regionMapper;

    @Resource
    private ServeMapper serveMapper;

    /**
     * 分页查询服务列表
     * @param servePageQueryReqDTO 查询条件
     * @return 分页列表
     */
    @Override
    public PageResult<ServeResDTO> page(ServePageQueryReqDTO servePageQueryReqDTO) {
        //调用mapper查询数据，这里由于继承了ServiceImpl<ServeMapper, Serve>，使用baseMapper相当于使用ServeMapper
        PageResult<ServeResDTO> serveResDTOPageResult = PageHelperUtils.selectPage(servePageQueryReqDTO, () -> baseMapper.queryServeListByRegionId(servePageQueryReqDTO.getRegionId()));
        return serveResDTOPageResult;
    }

    @Override
    @Transactional
    public void batchAdd(List<ServeUpsertReqDTO> serveUpsertReqDTOList) {
        for (ServeUpsertReqDTO serveUpsertReqDTO : serveUpsertReqDTOList) {
            //1.校验服务项是否为启用状态，不是启用状态不能新增
            ServeItem serveItem = serveItemMapper.selectById(serveUpsertReqDTO.getServeItemId());
            //如果服务项信息不存在或未启用
            if(ObjectUtil.isNull(serveItem) || serveItem.getActiveStatus()!= FoundationStatusEnum.ENABLE.getStatus()){
                throw new ForbiddenOperationException("该服务未启用无法添加到区域下使用");
            }

            //2.校验是否重复新增
            Integer count = lambdaQuery()
                    .eq(Serve::getRegionId, serveUpsertReqDTO.getRegionId())
                    .eq(Serve::getServeItemId, serveUpsertReqDTO.getServeItemId())
                    .count();
            if(count>0){
                throw new ForbiddenOperationException(serveItem.getName()+"服务已存在");
            }

            //3.新增服务
            Serve serve = BeanUtil.toBean(serveUpsertReqDTO, Serve.class);
            Region region = regionMapper.selectById(serveUpsertReqDTO.getRegionId());
            serve.setCityCode(region.getCityCode());
            baseMapper.insert(serve);
            //todo 上面这个是对每一个服务进行一条一条插入，能否优化为一次性批量写入数据库？
        }
    }

    //batchAdd优化后参考以下代码
//  @Override
//  @Transactional
//    public void batchAdd(List<ServeUpsertReqDTO> serveUpsertReqDTOList) {
//        //1.批量查询服务项信息，校验服务项是否为启用状态
//        List<Long> serveItemIds = serveUpsertReqDTOList.stream()
//                .map(ServeUpsertReqDTO::getServeItemId)
//                .distinct()
//                .collect(Collectors.toList());
//        List<ServeItem> serveItems = serveItemMapper.selectBatchIds(serveItemIds);
//        Map<Long, ServeItem> serveItemMap = serveItems.stream()
//                .collect(Collectors.toMap(ServeItem::getId, item -> item));
//
//        for (ServeUpsertReqDTO dto : serveUpsertReqDTOList) {
//            ServeItem serveItem = serveItemMap.get(dto.getServeItemId());
//            if (ObjectUtil.isNull(serveItem) || serveItem.getActiveStatus() != FoundationStatusEnum.ENABLE.getStatus()) {
//                throw new ForbiddenOperationException("该服务未启用无法添加到区域下使用");
//            }
//        }
//
//        //2.批量校验是否重复新增
//        List<Long> regionIds = serveUpsertReqDTOList.stream()
//                .map(ServeUpsertReqDTO::getRegionId)
//                .distinct()
//                .collect(Collectors.toList());
//
//        List<Serve> existingServes = lambdaQuery()
//                .in(Serve::getRegionId, regionIds)
//                .in(Serve::getServeItemId, serveItemIds)
//                .list();
//        Map<String, Boolean> existingMap = existingServes.stream()
//                .collect(Collectors.toMap(
//                        s -> s.getRegionId() + "_" + s.getServeItemId(),
//                        s -> true,
//                        (v1, v2) -> v1
//                ));
//
//        for (ServeUpsertReqDTO dto : serveUpsertReqDTOList) {
//            String key = dto.getRegionId() + "_" + dto.getServeItemId();
//            if (existingMap.containsKey(key)) {
//                ServeItem serveItem = serveItemMap.get(dto.getServeItemId());
//                throw new ForbiddenOperationException(serveItem.getName() + "服务已存在");
//            }
//        }
//
//        //3.批量查询区域信息，获取城市编码
//        List<Region> regions = regionMapper.selectBatchIds(regionIds);
//        Map<Long, Region> regionMap = regions.stream()
//                .collect(Collectors.toMap(Region::getId, r -> r));
//
//        //4.构建Serve对象列表，批量插入
//        List<Serve> serveList = serveUpsertReqDTOList.stream().map(dto -> {
//            Serve serve = BeanUtil.toBean(dto, Serve.class);
//            Region region = regionMap.get(dto.getRegionId());
//            serve.setCityCode(region.getCityCode());
//            return serve;
//        }).collect(Collectors.toList());
//
//        saveBatch(serveList);
//    }

    @Override
    @Transactional
    public Serve update(Long id, BigDecimal price) {
        //1.更新服务价格
        boolean update = lambdaUpdate()
                .eq(Serve::getId, id)
                .set(Serve::getPrice, price)
                .update();
        if(!update){
            throw new CommonException("修改服务价格失败");
        }
        return baseMapper.selectById(id);
    }
}
