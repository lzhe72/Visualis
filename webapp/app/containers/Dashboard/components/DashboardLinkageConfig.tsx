/*
 * <<
 * Davinci
 * ==
 * Copyright (C) 2016 - 2017 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

import * as React from 'react'
import { Button, Modal } from 'antd'

import { SQL_NUMBER_TYPES, DEFAULT_SPLITER } from 'app/globalConstants'
import { decodeMetricName, getAggregatorLocale } from 'containers/Widget/components/util'
import { IFormedViews } from 'containers/View/types'
import { IWidgetConfig } from 'containers/Widget/components/Widget'
import LinkageConfig from 'components/Linkages/LinkageConfig'

const styles = require('../Dashboard.less')

interface IDashboardLinkageConfigProps {
  currentDashboard: any
  currentItems: any[]
  currentItemsInfo: any
  linkages: any[]
  views: IFormedViews
  widgets: any[]
  visible: boolean
  loading: boolean
  onCancel: () => void
  onSave: (filterItems: any[]) => void
  onGetWidgetInfo: (itemId: number) => void
}

interface IDashboardLinkageConfigStates {
  linkageCascaderSource: any[]
  savingLinkageConfig: boolean
}

export class DashboardLinkageConfig extends React.Component<IDashboardLinkageConfigProps, IDashboardLinkageConfigStates> {

  public constructor (props: IDashboardLinkageConfigProps) {
    super(props)
    this.state = {
      linkageCascaderSource: [],
      savingLinkageConfig: false
    }
  }

  public componentWillReceiveProps (nextProps: IDashboardLinkageConfigProps) {
    const { visible } = nextProps
    if (visible) {
      const linkageCascaderSource = this.getLinkageConfigSource()
      this.setState({ linkageCascaderSource })
    }
  }

  private getLinkageConfigSource = () => {
    const { currentItems, widgets, views, currentItemsInfo } = this.props
    if (!currentItemsInfo) { return [] }

    const linkageConfigSource = []
    Object.keys(currentItemsInfo).forEach((k) => {
      const dashboardItem = currentItems.find((ci) => `${ci.id}` === k)
      const widget = widgets.find((w) => w.id === dashboardItem.widgetId)
      const widgetConfig: IWidgetConfig = JSON.parse(widget.config)
      const { cols, rows, metrics } = widgetConfig

      const view = views[widget.viewId]
      const { model, variable } = view

      // Cascader value ????????? itemId????????????????????????/???????????? ?????????????????? DEFAULT_SPLITER ??????
      const columns = [
        ...[...cols, ...rows]
          .filter(({ name }) => model[name])
          .map(({ name }) => {
            return {
              label: name,
              value: [name, model[name].sqlType, 'column'].join(DEFAULT_SPLITER)
            }
          }),
        ...metrics.map(({ name, agg }) => {
          const metricName = decodeMetricName(name)
          return {
            label: `${getAggregatorLocale(agg)} ${metricName}`,
            value: [`${agg}(${metricName})`, SQL_NUMBER_TYPES[SQL_NUMBER_TYPES.length - 1], 'column'].join(DEFAULT_SPLITER)
          }
        })
      ]

      const variables = variable.map(({ name }) => {
        return {
          label: `${name}[??????]`,
          value: [name, null, 'variable'].join(DEFAULT_SPLITER)
        }
      })

      linkageConfigSource.push({
        label: widget.name,
        value: k,
        children: {
          columns,
          variables
        }
      })
    })

    return linkageConfigSource
  }

  private onSavingLinkageConfig = () => {
    this.setState({
      savingLinkageConfig: !this.state.savingLinkageConfig
    })
  }

  public render () {
    const { visible, loading, onSave, onGetWidgetInfo, linkages, onCancel } = this.props
    const { linkageCascaderSource, savingLinkageConfig } = this.state

    const modalButtons = [(
      <Button
        key="cancel"
        size="large"
        onClick={onCancel}
      >
        ??? ???
      </Button>
    ), (
      <Button
        key="submit"
        size="large"
        type="primary"
        loading={loading}
        disabled={loading}
        onClick={this.onSavingLinkageConfig}
      >
        ??? ???
      </Button>
    )]

    return (
      <Modal
        title="??????????????????"
        wrapClassName="ant-modal-large"
        maskClosable={false}
        visible={visible}
        onCancel={onCancel}
        footer={modalButtons}
      >
        <div className={styles.modalLinkageConfig}>
          <LinkageConfig
            linkages={linkages}
            cascaderSource={linkageCascaderSource}
            onGetWidgetInfo={onGetWidgetInfo}
            saving={savingLinkageConfig}
            onSave={onSave}
          />
        </div>
      </Modal>
    )
  }
}

export default DashboardLinkageConfig
